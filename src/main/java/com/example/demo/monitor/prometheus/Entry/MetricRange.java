package com.example.demo.monitor.prometheus.Entry;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 单指标的区间查询结果。
 */
public record MetricRange(
        String metric,
        String unit,
        String uri,
        String method,
        Instant start,
        Instant end,
        String step,
        List<Series> series,
        int pointCount,
        boolean truncated) {

    public record Series(Map<String, String> labels, List<Point> points) {
    }

    public record Point(Instant timestamp, Double value) {
    }
}
