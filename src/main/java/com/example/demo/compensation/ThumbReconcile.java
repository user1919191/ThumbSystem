package com.example.demo.compensation;

import com.example.demo.MQ.thumb.msg.ThumbEvent;
import com.example.demo.constant.ThumbConstant;
import com.example.demo.model.entity.thumb;
import com.example.demo.service.ThumbService;
import com.google.common.collect.Sets;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.pulsar.core.PulsarTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 *  对账操作
 */

@Component
@Slf4j
public class ThumbReconcile {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private ThumbService thumbService;

    @Resource
    private PulsarTemplate<ThumbEvent> pulsarTemplate;

    @Scheduled(cron = "0 0 2 * * ?")
    public void reconcile() {
        long beginTime = System.currentTimeMillis();

        HashSet<Long> userIdSet = new HashSet<>();
        String pattern = ThumbConstant.USER_THUMB_KEY_PREFIX + "*";
        try(
                Cursor<String> scan = stringRedisTemplate.scan(ScanOptions.scanOptions().match(pattern).count(1000).build())
        ){
            while(scan.hasNext()){
                String key = scan.next();
                Long userId = Long.valueOf(key.replace(ThumbConstant.USER_THUMB_KEY_PREFIX, ""));
                userIdSet.add(userId);
            }
        }catch (Exception e){
            log.error("scan redis error", e);
        }

        userIdSet.forEach(userId ->{
            Set<Long> redisBlogSet= stringRedisTemplate.opsForHash().keys(ThumbConstant.USER_THUMB_KEY_PREFIX + userId).stream().map(obj -> {
                    return Long.valueOf(obj.toString());
                }).collect(Collectors.toSet());
            Set<Long> mysqlBlogIds = Optional.ofNullable(thumbService.lambdaQuery()
                            .eq(thumb::getUserId, userId)
                            .list()
                    ).orElse(new ArrayList<>())
                    .stream()
                    .map(thumb::getBlogId)
                    .collect(Collectors.toSet());

            Set<Long> diffBlogIds = Sets.difference(redisBlogSet, mysqlBlogIds);

            sendCompensationEvents(userId, diffBlogIds);
        });

        log.info("对账完成，耗时:{}", System.currentTimeMillis() - beginTime);
    }

    /**
     * 发送补偿事件到Pulsar
     */
    private void sendCompensationEvents(Long userId, Set<Long> blogIds) {
        blogIds.forEach(blogId -> {
            ThumbEvent thumbEvent = new ThumbEvent(userId, blogId, ThumbEvent.ThumbType.INCREASE, LocalDateTime.now());
            pulsarTemplate.sendAsync("thumb-topic", thumbEvent)
                    .exceptionally(ex -> {
                        log.error("补偿事件发送失败: userId={}, blogId={}", userId, blogId, ex);
                        return null;
                    });
        });
    }

}
