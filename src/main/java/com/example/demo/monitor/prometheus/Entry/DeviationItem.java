package com.example.demo.monitor.prometheus.Entry;

import java.util.Map;

/**
 * 单个序列的偏差结果。
 */
public record DeviationItem(
        String seriesKey,
        Map<String, String> labels,
        Double current,
        Double baseline,
        Double absoluteDelta,
        Double relativeDelta,
        String direction,
        String rankBasis,
        Double rankValue) {
}
