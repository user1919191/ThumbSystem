package com.example.demo.monitor.prometheus.Entry;

import java.time.Instant;
import java.util.List;

/**
 * 一次批量指标查询的结果。
 */
public record MetricBatch(
        String uri,
        String method,
        String window,
        List<Value> metrics,
        Instant measuredAt) {

    public record Value(String metric, String unit, String description, Double value) {
    }
}
