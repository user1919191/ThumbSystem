package com.example.demo.Agent.Service.impl;

import cn.hutool.core.date.DateTime;
import com.example.demo.Agent.Logs.LogSearchResult;
import com.example.demo.Agent.Logs.LogSearchService;
import com.example.demo.Agent.Service.ToolsService;
import com.example.demo.constant.ThumbConstant;
import com.example.demo.manager.Cache.CacheManager;
import com.example.demo.mapper.BlogMapper;
import com.example.demo.mapper.BizRetryTaskMapper;
import com.example.demo.model.entity.BizRetryTask;
import com.example.demo.model.entity.blog;
import com.example.demo.model.entity.thumb;
import com.example.demo.service.ThumbService;
import com.example.demo.monitor.prometheus.Entry.DeviationResult;
import com.example.demo.monitor.prometheus.Entry.EndpointMetrics;
import com.example.demo.monitor.prometheus.EndpointMetricsQueryService;
import com.example.demo.monitor.prometheus.Entry.MetricBatch;
import com.example.demo.monitor.prometheus.Entry.MetricRange;
import com.example.demo.monitor.prometheus.Entry.OverviewMetrics;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.apache.pulsar.client.api.Message;
import org.apache.pulsar.client.api.MessageId;
import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.client.api.Reader;
import org.apache.pulsar.client.api.Schema;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.data.redis.connection.DataType;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.lang.management.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ToolsServiceImpl implements ToolsService {

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 100;
    private static final int MAX_PAYLOAD_CHARS = 4096;

    private final BizRetryTaskMapper retryTaskMapper;
    private final PulsarClient pulsarClient;
    private final EndpointMetricsQueryService endpointMetricsQueryService;
    private final LogSearchService logSearchService;
    private final BlogMapper blogMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final RedissonClient redissonClient;
    private final CacheManager cacheManager;
    private final ThumbService thumbService;

    @Override
    @Tool(description = "Current JVM memory,cumulative GC statistics")
    public Map<String, Object> queryJvmGcStatus() {
        // 获取JVM整体信息
        MemoryUsage heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
        Map<String, Object> jvm = new LinkedHashMap<>();
        jvm.put("heapUsedBytes", heap.getUsed());
        jvm.put("heapCommittedBytes", heap.getCommitted());
        jvm.put("heapMaxBytes", heap.getMax());
        jvm.put("threadCount", ManagementFactory.getThreadMXBean().getThreadCount());
        jvm.put("uptimeMs", ManagementFactory.getRuntimeMXBean().getUptime());

        //获取GC信息
        List<Map<String, Object>> gc = new ArrayList<>();
        for (GarbageCollectorMXBean collector : ManagementFactory.getGarbageCollectorMXBeans()) {
            gc.add(Map.of("name", collector.getName(), "collectionCount", collector.getCollectionCount(),
                    "collectionTimeMs", collector.getCollectionTime()));
        }

        //获取内存池详细信息
        List<MemoryPoolMXBean> memoryPoolMXBeans = ManagementFactory.getMemoryPoolMXBeans();
        LinkedHashMap<String, MemoryUsage> memoryUsageLinkedHashMap = memoryPoolMXBeans.stream().collect(Collectors.toMap(MemoryPoolMXBean::getName, MemoryPoolMXBean::getUsage, (a, b) -> b,
                LinkedHashMap::new));
        String memoryUsage = memoryUsageLinkedHashMap.toString();

        return Map.of("capturedAt", Instant.now().toString(), "jvm", jvm,
                "gc", gc, "memoryUsage", memoryUsage);
    }

    @Override
    @Tool(description = "Current HTTP QPS and success rate from Prometheus")
    public Map<String, Object> queryQpsStatus() {
        try {
            OverviewMetrics metrics = endpointMetricsQueryService.queryOverview();
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("source", "prometheus");
            result.put("available", true);
            result.put("qps", metrics.qps());
            result.put("window", metrics.qpsWindow());
            result.put("successRate", metrics.successRate());
            result.put("successWindow", metrics.successWindow());
            result.put("requestCountSinceStart", metrics.totalRequests());
            result.put("measuredAt", metrics.measuredAt().toString());
            return result;
        } catch (RuntimeException exception) {
            return prometheusError(exception);
        }
    }

    @Override
    @Tool(description = "Query QPS, success rate and P99 of a specific HTTP endpoint from Prometheus")
    public Map<String, Object> queryEndpointMetrics(
            @ToolParam(description = "URI template, e.g. /api/thumb/do") String uri,
            @ToolParam(description = "HTTP method: GET/POST/PUT/DELETE/PATCH") String method,
            @ToolParam(description = "Window: 1m/5m/15m/30m/1h/6h/24h") String window) {
        try {
            EndpointMetrics metrics = endpointMetricsQueryService.query(uri, method, window);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("source", "prometheus");
            result.put("available", true);
            result.put("uri", metrics.uri());
            result.put("method", metrics.method());
            result.put("window", metrics.window());
            result.put("qps", metrics.qps());
            result.put("successRate", metrics.successRate());
            result.put("p99Seconds", metrics.p99Seconds());
            result.put("measuredAt", metrics.measuredAt().toString());
            return result;
        } catch (RuntimeException exception) {
            return prometheusError(exception);
        }
    }

    @Override
    @Tool(description = "Query multiple Prometheus metrics in one call. "
            + "Metrics: qps,success_rate,error_rate,p99,avg_latency,heap_usage,nonheap_used,"
            + "live_threads,deadlocked_threads,process_cpu,classes_loaded,gc_time_ratio,gc_pause_max,full_gc_count")
    public Map<String, Object> queryMetricsBatch(
            @ToolParam(description = "Comma-separated metric keys, e.g. qps,success_rate,p99") String metrics,
            @ToolParam(description = "URI template, e.g. /api/thumb/do; empty means all endpoints") String uri,
            @ToolParam(description = "HTTP method; required when uri is set") String method,
            @ToolParam(description = "Window: 1m/5m/15m/30m/1h/6h/24h") String window) {
        try {
            MetricBatch batch = endpointMetricsQueryService.queryMetricsBatch(metrics, uri, method, window);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("source", "prometheus");
            result.put("available", true);
            result.put("uri", batch.uri());
            result.put("method", batch.method());
            result.put("window", batch.window());
            result.put("metrics", batch.metrics());
            result.put("measuredAt", batch.measuredAt().toString());
            return result;
        } catch (RuntimeException exception) {
            return prometheusError(exception);
        }
    }

    @Override
    @Tool(description = "Query one metric time series from Prometheus over a time range. "
            + "Use it for trends, spikes and before/after comparisons. "
            + "Supported metrics are the same as queryMetricsBatch")
    public Map<String, Object> queryMetricsRange(
            @ToolParam(description = "Metric key, e.g. qps or heap_usage") String metric,
            @ToolParam(description = "URI template; empty means all endpoints") String uri,
            @ToolParam(description = "HTTP method; required when uri is set") String method,
            @ToolParam(description = "Start time: ISO-8601, yyyy-MM-dd HH:mm:ss, or epoch seconds; empty = 1 hour ago") String startTime,
            @ToolParam(description = "End time: same formats; empty = now") String endTime,
            @ToolParam(description = "Step: 15s/30s/1m/5m; empty = 15s") String step,
            @ToolParam(description = "PromQL window for rate/increase: 1m/5m/15m/30m/1h/6h/24h; empty = 5m") String window) {
        try {
            MetricRange range = endpointMetricsQueryService.queryMetricRange(
                    metric, uri, method, startTime, endTime, step, window);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("source", "prometheus");
            result.put("available", true);
            result.put("metric", range.metric());
            result.put("unit", range.unit());
            result.put("uri", range.uri());
            result.put("method", range.method());
            result.put("start", range.start().toString());
            result.put("end", range.end().toString());
            result.put("step", range.step());
            result.put("pointCount", range.pointCount());
            result.put("truncated", range.truncated());
            result.put("series", range.series());
            return result;
        } catch (RuntimeException exception) {
            return prometheusError(exception);
        }
    }

    @Override
    @Tool(description = "Rank top deviating HTTP endpoints or JVM metrics against a baseline time. "
            + "Endpoint metrics: qps,success_rate,error_rate,p99,avg_latency; "
            + "global metrics: heap_usage,nonheap_used,live_threads,deadlocked_threads,process_cpu,"
            + "classes_loaded,gc_time_ratio,gc_pause_max,full_gc_count")
    public Map<String, Object> queryTopDeviation(
            @ToolParam(description = "Metric key, e.g. qps or p99") String metric,
            @ToolParam(description = "Baseline: 30m/1h/6h/12h/1d/7d; default 1h") String baseline,
            @ToolParam(description = "Rate window: 1m/5m/15m/30m/1h/6h/24h; default 5m") String window,
            @ToolParam(description = "Top N, capped at 20") Integer topN,
            @ToolParam(description = "Minimum absolute relative deviation, e.g. 0.1 = 10%; default 0") Double minDeviation) {
        try {
            DeviationResult result = endpointMetricsQueryService.queryTopDeviation(
                    metric, baseline, window, topN, minDeviation);

            Map<String, Object> map = new LinkedHashMap<>();
            map.put("source", "prometheus");
            map.put("available", true);
            map.put("metric", result.metric());
            map.put("unit", result.unit());
            map.put("window", result.window());
            map.put("baseline", result.baseline());
            map.put("baselineAt", result.baselineAt().toString());
            map.put("minDeviation", result.minDeviation());
            map.put("itemCount", result.items().size());
            map.put("items", result.items());
            map.put("measuredAt", result.measuredAt().toString());
            return map;
        } catch (RuntimeException exception) {
            return prometheusError(exception);
        }
    }

    @Override
    @Tool(description = "Search local application log files by keyword, level and time range. "
            + "Returns the newest matching lines first; no cloud log platform required")
    public Map<String, Object> queryLogs(
            @ToolParam(description = "Case-insensitive keyword, e.g. eventId or exception message; empty matches all") String keyword,
            @ToolParam(description = "Log level: TRACE/DEBUG/INFO/WARN/ERROR; empty for all") String level,
            @ToolParam(description = "Start time: yyyy-MM-dd HH:mm:ss.SSS, ISO-8601, or epoch seconds; empty for no limit") String startTime,
            @ToolParam(description = "End time: same formats; empty for no limit") String endTime,
            @ToolParam(description = "Maximum lines, capped at 100") Integer limit) {
        try {
            LogSearchResult logs = logSearchService.search(keyword, level, startTime, endTime, limit);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("source", "local-file");
            result.put("available", true);
            result.put("dir", logs.dir());
            result.put("scannedFiles", logs.scannedFiles());
            result.put("scannedLines", logs.scannedLines());
            result.put("count", logs.count());
            result.put("truncated", logs.truncated());
            result.put("items", logs.items());
            return result;
        } catch (RuntimeException exception) {
            return logError(exception);
        }
    }

    private Map<String, Object> logError(Exception exception) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("source", "local-file");
        result.put("available", false);
        result.put("error", exception.getMessage());
        result.put("measuredAt", Instant.now().toString());
        return result;
    }

    @Override
    @Tool(description = "Read-only thumb evidence: Redis value/TTL, DB detail existence, "
            + "blog thumbCount and actual detail count. Use before any compensation")
    public Map<String, Object> queryThumbEvidence(
            @ToolParam(description = "User ID") Long userId,
            @ToolParam(description = "Blog ID") Long blogId) {
        if (invalidIds(userId, blogId)) {
            return invalidIdResult();
        }
        try {
            String hashKey = ThumbConstant.USER_THUMB_KEY_PREFIX + userId;
            String field = blogId.toString();

            Object redisValue = stringRedisTemplate.opsForHash().get(hashKey, field);
            Long redisTtlSeconds = stringRedisTemplate.getExpire(hashKey, TimeUnit.SECONDS);
            boolean redisHasThumb = isThumbValue(redisValue);

            long dbDetailCount = thumbService.count(new LambdaQueryWrapper<thumb>()
                    .eq(thumb::getUserId, userId)
                    .eq(thumb::getBlogId, blogId));
            boolean dbThumbExists = dbDetailCount > 0;

            long actualDetailCount = thumbService.count(new LambdaQueryWrapper<thumb>()
                    .eq(thumb::getBlogId, blogId));
            blog blogRow = blogMapper.selectById(blogId);
            Integer blogThumbCount = blogRow == null ? null : blogRow.getThumbCount();
            long countValue = blogThumbCount == null ? -1L : blogThumbCount.longValue();

            boolean consistent = redisHasThumb == dbThumbExists && countValue == actualDetailCount;

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("source", "redis+db");
            result.put("available", true);
            result.put("userId", userId);
            result.put("blogId", blogId);
            result.put("redisHasThumb", redisHasThumb);
            result.put("redisValue", redisValue == null ? null : String.valueOf(redisValue));
            result.put("redisTtlSeconds", redisTtlSeconds);
            result.put("dbThumbExists", dbThumbExists);
            result.put("dbDetailCount", dbDetailCount);
            result.put("blogThumbCount", blogThumbCount);
            result.put("actualDetailCount", actualDetailCount);
            result.put("consistent", consistent);
            result.put("checkedAt", Instant.now().toString());
            return result;
        } catch (RuntimeException exception) {
            return redisDbError(exception);
        }
    }

    @Override
    @Tool(description = "Read-only Redis hash info for a thumb key. Key must start with 'thumb:'. "
            + "Returns type, TTL, size and up to 100 entries")
    public Map<String, Object> queryRedisInfo(
            @ToolParam(description = "Redis key, e.g. thumb:2002") String key) {
        if (key == null || key.isBlank() || !key.trim().startsWith(ThumbConstant.USER_THUMB_KEY_PREFIX)) {
            return redisDbError(new IllegalArgumentException("key 必须以 thumb: 开头"));
        }
        String normalizedKey = key.trim();
        try {
            boolean exists = Boolean.TRUE.equals(stringRedisTemplate.hasKey(normalizedKey));
            DataType type = stringRedisTemplate.type(normalizedKey);
            Long ttlSeconds = stringRedisTemplate.getExpire(normalizedKey, TimeUnit.SECONDS);

            Long size = null;
            List<Map<String, Object>> entries = new ArrayList<>();
            boolean truncated = false;

            if (exists && DataType.HASH.equals(type)) {
                size = stringRedisTemplate.opsForHash().size(normalizedKey);
                ScanOptions options = ScanOptions.scanOptions().count(100).build();
                try (Cursor<Map.Entry<Object, Object>> cursor =
                             stringRedisTemplate.opsForHash().scan(normalizedKey, options)) {
                    while (cursor.hasNext()) {
                        if (entries.size() >= MAX_LIMIT) {
                            truncated = true;
                            break;
                        }
                        Map.Entry<Object, Object> entry = cursor.next();
                        Map<String, Object> item = new LinkedHashMap<>();
                        item.put("field", String.valueOf(entry.getKey()));
                        item.put("value", String.valueOf(entry.getValue()));
                        entries.add(item);
                    }
                }
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("source", "redis");
            result.put("available", true);
            result.put("key", normalizedKey);
            result.put("exists", exists);
            result.put("type", type == null ? null : type.code());
            result.put("ttlSeconds", ttlSeconds);
            result.put("size", size);
            result.put("entryCount", entries.size());
            result.put("truncated", truncated);
            result.put("entries", entries);
            result.put("checkedAt", Instant.now().toString());
            return result;
        } catch (RuntimeException exception) {
            return redisDbError(exception);
        }
    }

    @Override
    @Tool(description = "Read-only DB info by key. Key format: userId:blogId or blogId. "
            + "Returns blog thumbCount, actual detail count and thumb existence")
    public Map<String, Object> queryDBInfo(
            @ToolParam(description = "Key: userId:blogId or blogId") String key) {
        if (key == null || key.isBlank()) {
            return redisDbError(new IllegalArgumentException("key 不能为空"));
        }
        String normalized = key.trim();
        Long userId = null;
        Long blogId;
        try {
            String[] parts = normalized.split(":");
            if (parts.length == 2) {
                userId = Long.parseLong(parts[0].trim());
                blogId = Long.parseLong(parts[1].trim());
            } else if (parts.length == 1) {
                blogId = Long.parseLong(parts[0].trim());
            } else {
                return redisDbError(new IllegalArgumentException("key 格式应为 userId:blogId 或 blogId"));
            }
        } catch (NumberFormatException e) {
            return redisDbError(new IllegalArgumentException("key 必须是数字：userId:blogId 或 blogId"));
        }

        if (blogId == null || blogId <= 0 || (userId != null && userId <= 0)) {
            return redisDbError(new IllegalArgumentException("userId/blogId 必须大于 0"));
        }

        try {
            blog blogRow = blogMapper.selectById(blogId);
            long actualDetailCount = thumbService.count(new LambdaQueryWrapper<thumb>()
                    .eq(thumb::getBlogId, blogId));

            Long thumbId = null;
            boolean thumbExists = false;
            if (userId != null) {
                List<thumb> thumbRows = thumbService.list(new LambdaQueryWrapper<thumb>()
                        .eq(thumb::getUserId, userId)
                        .eq(thumb::getBlogId, blogId)
                        .last("LIMIT 1"));
                if (!thumbRows.isEmpty()) {
                    thumbExists = true;
                    thumbId = thumbRows.get(0).getId();
                }
            }

            Integer blogThumbCount = blogRow == null ? null : blogRow.getThumbCount();
            long countValue = blogThumbCount == null ? -1L : blogThumbCount.longValue();

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("source", "db");
            result.put("available", true);
            result.put("key", normalized);
            result.put("userId", userId);
            result.put("blogId", blogId);
            result.put("blogExists", blogRow != null);
            result.put("blogThumbCount", blogThumbCount);
            result.put("actualDetailCount", actualDetailCount);
            result.put("countConsistent", countValue == actualDetailCount);
            result.put("thumbExists", thumbExists);
            result.put("thumbId", thumbId);
            result.put("checkedAt", Instant.now().toString());
            return result;
        } catch (RuntimeException exception) {
            return redisDbError(exception);
        }
    }

    @Override
    @Tool(description = "Insert thumb state into Redis. dryRun defaults to true; "
            + "set false only after evidence review")
    public Map<String, Object> insertRedisThumb(
            @ToolParam(description = "User ID") Long userId,
            @ToolParam(description = "Blog ID") Long blogId,
            @ToolParam(description = "true = only check, false = execute; default true") Boolean dryRun) {
        if (invalidIds(userId, blogId)) {
            return invalidIdResult();
        }
        boolean execute = Boolean.FALSE.equals(dryRun);
        boolean dryRunFlag = !execute;

        return executeWithUserLock(userId, "INSERT_REDIS", dryRunFlag, () -> {
            String hashKey = ThumbConstant.USER_THUMB_KEY_PREFIX + userId;
            String field = blogId.toString();
            Object current = stringRedisTemplate.opsForHash().get(hashKey, field);

            if (isThumbValue(current)) {
                return actionResult("INSERT_REDIS", dryRunFlag, false, "SKIPPED",
                        "Redis 已存在点赞状态", Map.of("currentValue", String.valueOf(current)));
            }
            if (!execute) {
                return actionResult("INSERT_REDIS", true, false, "PLANNED",
                        "DRY-RUN：将写入 Redis", Map.of("hashKey", hashKey, "field", field));
            }

            stringRedisTemplate.opsForHash().put(hashKey, field, "1");
            stringRedisTemplate.expire(hashKey, ThumbConstant.CACHE_EXPIRE_DAYS, TimeUnit.DAYS);
            cacheManager.putIfPresent(hashKey, field, "1");
            return actionResult("INSERT_REDIS", false, true, "EXECUTED",
                    "Redis 写入成功", Map.of("hashKey", hashKey, "field", field));
        });
    }

    @Override
    @Tool(description = "Delete thumb state from Redis. dryRun defaults to true; "
            + "set false only after evidence review")
    public Map<String, Object> deleteRedisThumb(
            @ToolParam(description = "User ID") Long userId,
            @ToolParam(description = "Blog ID") Long blogId,
            @ToolParam(description = "true = only check, false = execute; default true") Boolean dryRun) {
        if (invalidIds(userId, blogId)) {
            return invalidIdResult();
        }
        boolean execute = Boolean.FALSE.equals(dryRun);
        boolean dryRunFlag = !execute;

        return executeWithUserLock(userId, "DELETE_REDIS", dryRunFlag, () -> {
            String hashKey = ThumbConstant.USER_THUMB_KEY_PREFIX + userId;
            String field = blogId.toString();
            Object current = stringRedisTemplate.opsForHash().get(hashKey, field);

            if (!isThumbValue(current)) {
                return actionResult("DELETE_REDIS", dryRunFlag, false, "SKIPPED",
                        "Redis 无点赞状态", Map.of("currentValue", String.valueOf(current)));
            }
            if (!execute) {
                return actionResult("DELETE_REDIS", true, false, "PLANNED",
                        "DRY-RUN：将删除 Redis 点赞状态", Map.of("hashKey", hashKey, "field", field));
            }

            stringRedisTemplate.opsForHash().delete(hashKey, field);
            cacheManager.putIfPresent(hashKey, field, ThumbConstant.UN_THUMB_CONSTANT);
            return actionResult("DELETE_REDIS", false, true, "EXECUTED",
                    "Redis 删除成功", Map.of("hashKey", hashKey, "field", field));
        });
    }

    @Override
    @Tool(description = "Insert thumb detail into DB and recompute blog.thumbCount. "
            + "dryRun defaults to true; set false only after evidence review")
    public Map<String, Object> insertDbThumb(
            @ToolParam(description = "User ID") Long userId,
            @ToolParam(description = "Blog ID") Long blogId,
            @ToolParam(description = "true = only check, false = execute; default true") Boolean dryRun) {
        if (invalidIds(userId, blogId)) {
            return invalidIdResult();
        }
        boolean execute = Boolean.FALSE.equals(dryRun);
        boolean dryRunFlag = !execute;

        return executeWithUserLock(userId, "INSERT_DB", dryRunFlag, () -> {
            blog blogRow = blogMapper.selectById(blogId);
            if (blogRow == null) {
                return actionResult("INSERT_DB", dryRunFlag, false, "FAILED",
                        "blog 不存在: " + blogId, null);
            }

            long existing = thumbService.count(new LambdaQueryWrapper<thumb>()
                    .eq(thumb::getUserId, userId)
                    .eq(thumb::getBlogId, blogId));
            if (existing > 0) {
                return actionResult("INSERT_DB", dryRunFlag, false, "SKIPPED",
                        "DB 已存在点赞记录", Map.of("dbDetailCount", existing));
            }
            if (!execute) {
                return actionResult("INSERT_DB", true, false, "PLANNED",
                        "DRY-RUN：将插入 thumb 并重算 blog.thumbCount",
                        Map.of("userId", userId, "blogId", blogId));
            }

            thumb thumbRow = new thumb();
            thumbRow.setUserId(userId);
            thumbRow.setBlogId(blogId);
            thumbRow.setCreateTime(DateTime.now());
            if (!thumbService.save(thumbRow)) {
                return actionResult("INSERT_DB", false, false, "FAILED",
                        "DB 插入失败", null);
            }

            int recomputed = recomputeBlogThumbCount(blogId);
            return actionResult("INSERT_DB", false, true, "EXECUTED",
                    "DB 插入成功并重算计数",
                    Map.of("thumbId", String.valueOf(thumbRow.getId()), "blogThumbCount", recomputed));
        });
    }

    @Override
    @Tool(description = "Delete thumb detail from DB and recompute blog.thumbCount. "
            + "HIGH RISK; dryRun defaults to true and must be reviewed before execution")
    public Map<String, Object> deleteDbThumb(
            @ToolParam(description = "User ID") Long userId,
            @ToolParam(description = "Blog ID") Long blogId,
            @ToolParam(description = "true = only check, false = execute; default true") Boolean dryRun) {
        if (invalidIds(userId, blogId)) {
            return invalidIdResult();
        }
        boolean execute = Boolean.FALSE.equals(dryRun);
        boolean dryRunFlag = !execute;

        return executeWithUserLock(userId, "DELETE_DB", dryRunFlag, () -> {
            long existing = thumbService.count(new LambdaQueryWrapper<thumb>()
                    .eq(thumb::getUserId, userId)
                    .eq(thumb::getBlogId, blogId));
            if (existing == 0) {
                return actionResult("DELETE_DB", dryRunFlag, false, "SKIPPED",
                        "DB 无点赞记录", null);
            }
            if (!execute) {
                return actionResult("DELETE_DB", true, false, "PLANNED",
                        "DRY-RUN：将删除 thumb 并重算 blog.thumbCount",
                        Map.of("userId", userId, "blogId", blogId, "matchedRows", existing));
            }

            boolean removed = thumbService.remove(new LambdaQueryWrapper<thumb>()
                    .eq(thumb::getUserId, userId)
                    .eq(thumb::getBlogId, blogId));
            if (!removed) {
                return actionResult("DELETE_DB", false, false, "FAILED",
                        "DB 删除失败", null);
            }

            int recomputed = recomputeBlogThumbCount(blogId);
            return actionResult("DELETE_DB", false, true, "EXECUTED",
                    "DB 删除成功并重算计数",
                    Map.of("matchedRows", existing, "blogThumbCount", recomputed));
        });
    }

    private Map<String, Object> executeWithUserLock(Long userId, String action, boolean dryRun,
                                                    Supplier<Map<String, Object>> body) {
        RLock lock = redissonClient.getLock(ThumbConstant.THUMB_LOCK_KEY_PREFIX + userId);
        boolean locked = false;
        try {
            locked = lock.tryLock(3, 10, TimeUnit.SECONDS);
            if (!locked) {
                return actionResult(action, dryRun, false, "FAILED",
                        "获取用户锁失败，请稍后重试", null);
            }
            return body.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return actionResult(action, dryRun, false, "FAILED", "获取用户锁被中断", null);
        } catch (RuntimeException e) {
            return actionResult(action, dryRun, false, "FAILED", e.getMessage(), null);
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private Map<String, Object> actionResult(String action, boolean dryRun, boolean executed,
                                             String status, String message,
                                             Map<String, Object> data) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("action", action);
        result.put("dryRun", dryRun);
        result.put("executed", executed);
        result.put("status", status);
        result.put("message", message);
        if (data != null) {
            result.put("data", data);
        }
        result.put("measuredAt", Instant.now().toString());
        return result;
    }

    private boolean invalidIds(Long userId, Long blogId) {
        return userId == null || userId <= 0 || blogId == null || blogId <= 0;
    }

    private Map<String, Object> invalidIdResult() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("source", "redis+db");
        result.put("available", false);
        result.put("error", "userId 和 blogId 必须大于 0");
        result.put("measuredAt", Instant.now().toString());
        return result;
    }

    private Map<String, Object> redisDbError(Exception exception) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("source", "redis+db");
        result.put("available", false);
        result.put("error", exception.getMessage());
        result.put("measuredAt", Instant.now().toString());
        return result;
    }

    private boolean isThumbValue(Object value) {
        return value != null
                && !String.valueOf(ThumbConstant.UN_THUMB_CONSTANT).equals(String.valueOf(value));
    }

    private int recomputeBlogThumbCount(Long blogId) {
        long count = thumbService.count(new LambdaQueryWrapper<thumb>()
                .eq(thumb::getBlogId, blogId));
        int value = (int) Math.min(count, (long) Integer.MAX_VALUE);
        blog update = new blog();
        update.setId(blogId);
        update.setThumbCount(value);
        blogMapper.updateById(update);
        return value;
    }

    private Map<String, Object> prometheusError(Exception exception) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("source", "prometheus");
        result.put("available", false);
        result.put("error", exception.getMessage());
        result.put("measuredAt", Instant.now().toString());
        return result;
    }

    @Override
    @Tool(description = "Read thumb-topic or thumb-log-topic without acknowledging messages")
    public Map<String, Object> queryPulsarMessages(
            @ToolParam(description = "thumb-topic or thumb-log-topic") String topic,
            @ToolParam(description = "Maximum messages, capped at 100") Integer limit) {
        String selected = topic == null || topic.isBlank() ? "thumb-topic" : topic.trim();
        if (!"thumb-topic".equals(selected) && !"thumb-log-topic".equals(selected)) {
            throw new IllegalArgumentException("Only thumb-topic and thumb-log-topic are allowed");
        }
        return readTopic(selected, limit);
    }

    @Override
    @Tool(description = "Read thumb-dlq-topic without creating a subscription or acknowledging messages")
    public Map<String, Object> queryDlqMessages(
            @ToolParam(description = "Maximum messages, capped at 100") Integer limit) {
        return readTopic("thumb-dlq-topic", limit);
    }

    private Map<String, Object> readTopic(String topic, Integer limit) {
        List<Map<String, Object>> items = new ArrayList<>();
        try (Reader<byte[]> reader = pulsarClient.newReader(Schema.BYTES)
                .topic(topic).startMessageId(MessageId.earliest).create()) {
            while (items.size() < boundedLimit(limit)) {
                Message<byte[]> message = reader.readNext(300, TimeUnit.MILLISECONDS);
                if (message == null) {
                    break;
                }
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("messageId", message.getMessageId().toString());
                item.put("publishTime", Instant.ofEpochMilli(message.getPublishTime()).toString());
                item.put("properties", message.getProperties());
                String payload = new String(message.getValue(), StandardCharsets.UTF_8);
                item.put("payload", payload.length() > MAX_PAYLOAD_CHARS
                        ? payload.substring(0, MAX_PAYLOAD_CHARS) : payload);
                items.add(item);
            }
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to read Pulsar topic: " + topic, exception);
        }
        return Map.of("topic", topic, "count", items.size(), "items", items);
    }

    @Override
    @Tool(description = "Read-only retry task lookup")
    public Map<String, Object> queryRetryTasks(
            @ToolParam(description = "Event ID; empty skips filter") String eventId,
            @ToolParam(description = "Business key; empty skips filter") String bizKey,
            @ToolParam(description = "Status; empty skips filter") String status,
            @ToolParam(description = "Maximum rows, capped at 100") Integer limit) {
        LambdaQueryWrapper<BizRetryTask> query = new LambdaQueryWrapper<>();
        if (eventId != null && !eventId.isBlank()) {
            query.eq(BizRetryTask::getEventId, eventId.trim());
        }
        if (bizKey != null && !bizKey.isBlank()) {
            query.eq(BizRetryTask::getBizKey, bizKey.trim());
        }
        if (status != null && !status.isBlank()) {
            query.eq(BizRetryTask::getStatus, status.trim());
        }
        query.orderByDesc(BizRetryTask::getCreatedAt).last("LIMIT " + boundedLimit(limit));
        List<BizRetryTask> tasks = retryTaskMapper.selectList(query);
        return Map.of("count", tasks.size(), "items", tasks);
    }

    private int boundedLimit(Integer limit) {
        return limit == null || limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);
    }
}
