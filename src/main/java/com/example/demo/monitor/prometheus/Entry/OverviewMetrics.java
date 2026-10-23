package com.example.demo.monitor.prometheus.Entry;

import java.time.Instant;

/**
 * 全站 HTTP 监控快照。
 */
public record OverviewMetrics(
        Double qps,
        Double successRate,
        Double totalRequests,
        String qpsWindow,
        String successWindow,
        Instant measuredAt) {
}
