package com.example.demo.monitor.prometheus.Config;

import lombok.Getter;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Agent 允许查询的指标白名单。
 */
@Getter
public enum MetricKey {

    QPS("qps", "req/s", "每秒请求数", true),
    SUCCESS_RATE("success_rate", "ratio", "非 5xx 请求占比", true),
    ERROR_RATE("error_rate", "ratio", "5xx 请求占比", true),
    P99("p99", "seconds", "P99 延迟", true),
    AVG_LATENCY("avg_latency", "seconds", "平均延迟", true),
    HEAP_USAGE("heap_usage", "ratio", "堆内存使用率", false),
    NONHEAP_USED("nonheap_used", "bytes", "非堆内存使用量", false),
    LIVE_THREADS("live_threads", "count", "活跃线程数", false),
    DEADLOCKED_THREADS("deadlocked_threads", "count", "死锁线程数", false),
    PROCESS_CPU("process_cpu", "ratio", "进程 CPU 使用率", false),
    CLASSES_LOADED("classes_loaded", "count", "已加载类数量", false),
    GC_TIME_RATIO("gc_time_ratio", "ratio", "GC 时间占比", false),
    GC_PAUSE_MAX("gc_pause_max", "seconds", "单次最长 GC 停顿", false),
    FULL_GC_COUNT("full_gc_count", "count", "Full GC 次数", false);

    private final String key;
    private final String unit;
    private final String description;
    private final boolean endpointScoped;

    MetricKey(String key, String unit, String description, boolean endpointScoped) {
        this.key = key;
        this.unit = unit;
        this.description = description;
        this.endpointScoped = endpointScoped;
    }

    public String toPromql(String matchers, String window) {
        return switch (this) {
            case QPS -> PromqlTemplates.qps(matchers, window);
            case SUCCESS_RATE -> PromqlTemplates.successRate(matchers, window);
            case ERROR_RATE -> PromqlTemplates.errorRate(matchers, window);
            case P99 -> PromqlTemplates.p99(matchers, window);
            case AVG_LATENCY -> PromqlTemplates.avgLatency(matchers, window);
            case HEAP_USAGE -> PromqlTemplates.heapUsage(matchers);
            case NONHEAP_USED -> PromqlTemplates.nonheapUsed(matchers);
            case LIVE_THREADS -> PromqlTemplates.liveThreads(matchers);
            case DEADLOCKED_THREADS -> PromqlTemplates.deadlockedThreads(matchers);
            case PROCESS_CPU -> PromqlTemplates.processCpu(matchers);
            case CLASSES_LOADED -> PromqlTemplates.classesLoaded(matchers);
            case GC_TIME_RATIO -> PromqlTemplates.gcTimeRatio(matchers, window);
            case GC_PAUSE_MAX -> PromqlTemplates.gcPauseMax(matchers, window);
            case FULL_GC_COUNT -> PromqlTemplates.fullGcCount(matchers, window);
        };
    }

    /**
     * 按接口维度分组，用于 top deviation 排序；JVM/GC 指标退化为单序列。
     */
    public String toGroupedPromql(String matchers, String window) {
        return switch (this) {
            case QPS -> PromqlTemplates.qpsByEndpoint(matchers, window);
            case SUCCESS_RATE -> PromqlTemplates.successRateByEndpoint(matchers, window);
            case ERROR_RATE -> PromqlTemplates.errorRateByEndpoint(matchers, window);
            case P99 -> PromqlTemplates.p99ByEndpoint(matchers, window);
            case AVG_LATENCY -> PromqlTemplates.avgLatencyByEndpoint(matchers, window);
            default -> toPromql(matchers, window);
        };
    }

    public static MetricKey from(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("metric 不能为空，可选: " + supportedKeys());
        }
        String normalized = raw.trim()
                .replaceAll("([a-z0-9])([A-Z])", "$1_$2")
                .toLowerCase(Locale.ROOT)
                .replace('-', '_');
        for (MetricKey metric : values()) {
            if (metric.key.equals(normalized)) {
                return metric;
            }
        }
        throw new IllegalArgumentException("不支持的 metric: " + raw + "，可选: " + supportedKeys());
    }

    public static String supportedKeys() {
        return Arrays.stream(values())
                .map(MetricKey::getKey)
                .collect(Collectors.joining(","));
    }
}
