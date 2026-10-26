package com.example.demo.compensation;

import com.example.demo.Agent.Controller.AgentServiceClient;
import com.example.demo.constant.ThumbConstant;
import com.example.demo.model.entity.thumb;
import com.example.demo.service.ThumbService;
import com.google.common.collect.Sets;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 对账操作：全量比对 Redis 与 DB，产出两组差异并交给 Agent
 */
@Component
@Slf4j
public class ThumbReconcile {

    private static final int KEY_SCAN_COUNT = 1000;

    private static final int FIELD_SCAN_COUNT = 500;

    /**
     * 单批差异上限：与设计文档“批量 > 100 需审批”对齐
     */
    private static final int BATCH_SIZE = 100;

    /**
     * Redis Hash 中表示“已点赞”的值
     */
    private static final String THUMBED_VALUE = "1";

    private static final String TRIGGER_SCHEDULED = "SCHEDULED";

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private ThumbService thumbService;

    @Resource
    private AgentServiceClient agentServiceClient;

    @Scheduled(cron = "0 0 2 * * ?")
    public void reconcile() {
        long beginTime = System.currentTimeMillis();
        String batchNo = "rc-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        List<ReconcileDiffItem> buffer = new ArrayList<>(BATCH_SIZE);
        int batchIndex = 0;
        int total = 0;

        String pattern = ThumbConstant.USER_THUMB_KEY_PREFIX + "*";
        try (Cursor<String> keyCursor = stringRedisTemplate.scan(
                ScanOptions.scanOptions().match(pattern).count(KEY_SCAN_COUNT).build())) {

            while (keyCursor.hasNext()) {
                String key = keyCursor.next();
                Long userId = parseUserId(key);
                if (userId == null) {
                    continue;
                }

                // 1. Redis 侧：只取 value == "1" 的 blogId
                Set<Long> redisBlogIds = scanRedisBlogIds(key);

                // 2. DB 侧：按 userId 一次性取回明细
                List<thumb> dbRows = thumbService.lambdaQuery()
                        .eq(thumb::getUserId, userId)
                        .list();
                Map<Long, thumb> dbMap = dbRows.stream()
                        .collect(Collectors.toMap(thumb::getBlogId, row -> row, (a, b) -> a));
                Set<Long> dbBlogIds = new HashSet<>(dbMap.keySet());

                // 3. 差异一：Redis 有、DB 无
                for (Long blogId : Sets.difference(redisBlogIds, dbBlogIds)) {
                    buffer.add(new ReconcileDiffItem(
                            diffKey(userId, blogId, ReconcileDiffType.REDIS_ONLY),
                            ReconcileDiffType.REDIS_ONLY,
                            userId,
                            blogId,
                            THUMBED_VALUE,
                            null,
                            null,
                            LocalDateTime.now()));
                }

                // 4. 差异二：DB 有、Redis 无（只比对缓存有效期内的数据）
                for (Long blogId : Sets.difference(dbBlogIds, redisBlogIds)) {
                    thumb row = dbMap.get(blogId);
                    if (!inCacheWindow(row.getCreateTime())) {
                        continue;
                    }
                    buffer.add(new ReconcileDiffItem(
                            diffKey(userId, blogId, ReconcileDiffType.DB_ONLY),
                            ReconcileDiffType.DB_ONLY,
                            userId,
                            blogId,
                            null,
                            row.getId(),
                            toLocalDateTime(row.getCreateTime()),
                            LocalDateTime.now()));
                }

                // 5. 满批即派发，避免大 key 撑爆内存
                while (buffer.size() >= BATCH_SIZE) {
                    List<ReconcileDiffItem> items = new ArrayList<>(buffer.subList(0, BATCH_SIZE));
                    buffer.subList(0, BATCH_SIZE).clear();
                    total += items.size();
                    dispatch(batchNo, batchIndex++, total, items);
                }
            }
        } catch (Exception e) {
            log.error("对账扫描失败, batchNo={}", batchNo, e);
        }

        if (!buffer.isEmpty()) {
            total += buffer.size();
            dispatch(batchNo, batchIndex, total, buffer);
        }

        log.info("对账完成, batchNo={}, totalDiff={}, costMs={}",
                batchNo, total, System.currentTimeMillis() - beginTime);
    }

    /**
     * 派发一批差异给 Agent，对账侧只负责发现差异，不直接补偿
     */
    private void dispatch(String batchNo, int batchIndex, int total, List<ReconcileDiffItem> items) {
        ReconcileDiffBatch batch = new ReconcileDiffBatch(
                batchNo, TRIGGER_SCHEDULED, batchIndex, total, List.copyOf(items));
        agentServiceClient.batchExecute(batch);
    }

    /**
     * HSCAN 读取用户的点赞 blogId 集合，只保留 value == "1" 的 field
     */
    private Set<Long> scanRedisBlogIds(String hashKey) {
        Set<Long> blogIds = new HashSet<>();
        try (Cursor<Map.Entry<Object, Object>> cursor = stringRedisTemplate.opsForHash()
                .scan(hashKey, ScanOptions.scanOptions().count(FIELD_SCAN_COUNT).build())) {
            while (cursor.hasNext()) {
                Map.Entry<Object, Object> entry = cursor.next();
                if (entry.getKey() == null
                        || entry.getValue() == null
                        || !THUMBED_VALUE.equals(entry.getValue().toString())) {
                    continue;
                }
                try {
                    blogIds.add(Long.valueOf(entry.getKey().toString()));
                } catch (NumberFormatException e) {
                    log.warn("非法 blogId, hashKey={}, field={}", hashKey, entry.getKey());
                }
            }
        } catch (Exception e) {
            log.error("scan redis hash error, hashKey={}", hashKey, e);
        }
        return blogIds;
    }

    private Long parseUserId(String key) {
        try {
            return Long.valueOf(key.substring(ThumbConstant.USER_THUMB_KEY_PREFIX.length()));
        } catch (Exception e) {
            log.warn("非法 thumb key: {}", key);
            return null;
        }
    }

    /**
     * DB_ONLY 必须限制在缓存有效期内，否则 7 天前的历史点赞必然是“DB 有、Redis 无”
     */
    private boolean inCacheWindow(Date createTime) {
        if (createTime == null) {
            return false;
        }
        Instant windowStart = Instant.now().minus(ThumbConstant.CACHE_EXPIRE_DAYS, ChronoUnit.DAYS);
        return createTime.toInstant().isAfter(windowStart);
    }

    private LocalDateTime toLocalDateTime(Date date) {
        return date == null ? null : LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault());
    }

    private String diffKey(Long userId, Long blogId, ReconcileDiffType diffType) {
        return userId + ":" + blogId + ":" + diffType.name();
    }
}
