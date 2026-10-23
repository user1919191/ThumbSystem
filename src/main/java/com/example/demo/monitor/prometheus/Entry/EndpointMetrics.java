package com.example.demo.monitor.prometheus.Entry;

import java.time.Instant;

/**
 * 单个接口的监控快照。
 */
public record EndpointMetrics(
        String uri,
        String method,
        String window,
        Double qps,
        Double successRate,
        Double p99Seconds,
        Instant measuredAt) {
}
