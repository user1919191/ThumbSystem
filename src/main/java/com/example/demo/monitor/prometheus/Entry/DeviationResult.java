package com.example.demo.monitor.prometheus.Entry;

import java.time.Instant;
import java.util.List;

/**
 * top deviation 查询结果。
 */
public record DeviationResult(
        String metric,
        String unit,
        String window,
        String baseline,
        Instant baselineAt,
        double minDeviation,
        List<DeviationItem> items,
        Instant measuredAt) {
}
