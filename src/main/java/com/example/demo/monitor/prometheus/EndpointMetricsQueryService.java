package com.example.demo.monitor.prometheus;

import com.example.demo.common.TimeParser;
import com.example.demo.monitor.prometheus.Config.MetricKey;
import com.example.demo.monitor.prometheus.Config.PrometheusProperties;
import com.example.demo.monitor.prometheus.Config.PromqlTemplates;
import com.example.demo.monitor.prometheus.Entry.DeviationItem;
import com.example.demo.monitor.prometheus.Entry.DeviationResult;
import com.example.demo.monitor.prometheus.Entry.EndpointMetrics;
import com.example.demo.monitor.prometheus.Entry.MetricBatch;
import com.example.demo.monitor.prometheus.Entry.MetricRange;
import com.example.demo.monitor.prometheus.Entry.OverviewMetrics;
import com.example.demo.monitor.prometheus.Entry.PrometheusResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 通过 Prometheus HTTP API 查询接口指标、批量指标、区间数据和偏差排序。
 */
@Service
@RequiredArgsConstructor
public class EndpointMetricsQueryService {

    private static final String ACTUATOR_EXCLUDE = ",uri!~\"/(api/)?actuator.*\"";
    private static final String DEFAULT_RANGE_WINDOW = "5m";
    private static final String DEFAULT_BASELINE = "1h";
    private static final int MAX_BATCH_METRICS = 10;
    private static final int MAX_RANGE_SERIES = 20;
    private static final int MAX_RANGE_POINTS = 1000;
    private static final int DEFAULT_TOP_N = 10;
    private static final int MAX_TOP_N = 20;
    private static final Pattern STEP_PATTERN = Pattern.compile("^(\\d+)([smhd])$");

    private final PrometheusQueryClient client;
    private final PrometheusProperties properties;

    /**
     * 查询指定接口的 QPS、成功率和 P99。
     */
    public EndpointMetrics query(String uri, String method, String window) {
        String effectiveWindow = normalizeWindow(window, properties.getDefaultEndpointWindow());
        String matchers = PromqlTemplates.endpointMatchers(baseMatchers(), uri, method);

        Double qps = firstValue(client.query(PromqlTemplates.qps(matchers, effectiveWindow)));
        Double successRate = firstValue(client.query(PromqlTemplates.successRate(matchers, effectiveWindow)));
        Double p99 = firstValue(client.query(PromqlTemplates.p99(matchers, effectiveWindow)));

        return new EndpointMetrics(uri, method, effectiveWindow, qps, successRate, p99, Instant.now());
    }

    /**
     * 查询全站（排除 actuator）的 QPS、成功率和累计请求数。
     */
    public OverviewMetrics queryOverview() {
        String qpsWindow = properties.getDefaultQpsWindow();
        String successWindow = properties.getDefaultSuccessWindow();
        String matchers = baseMatchers() + ACTUATOR_EXCLUDE;

        Double qps = firstValue(client.query(PromqlTemplates.qps(matchers, qpsWindow)));
        Double successRate = firstValue(client.query(PromqlTemplates.successRate(matchers, successWindow)));
        Double totalRequests = firstValue(client.query(PromqlTemplates.totalRequests(matchers)));

        return new OverviewMetrics(qps, successRate, totalRequests, qpsWindow, successWindow, Instant.now());
    }

    /**
     * 一次查询多个指标。
     */
    public MetricBatch queryMetricsBatch(String metrics, String uri, String method, String window) {
        String effectiveWindow = normalizeWindow(window, properties.getDefaultEndpointWindow());
        List<MetricKey> keys = parseMetricKeys(metrics);
        List<MetricBatch.Value> values = new ArrayList<>(keys.size());

        for (MetricKey key : keys) {
            String matchers = matchersFor(key, uri, method);
            Double value = firstValue(client.query(key.toPromql(matchers, effectiveWindow)));
            values.add(new MetricBatch.Value(key.getKey(), key.getUnit(), key.getDescription(), value));
        }
        return new MetricBatch(uri, method, effectiveWindow, values, Instant.now());
    }

    /**
     * 查询单个指标的区间数据。
     */
    public MetricRange queryMetricRange(String metric, String uri, String method,
                                        String startTime, String endTime,
                                        String stepText, String window) {
        MetricKey key = MetricKey.from(metric);
        String effectiveWindow = normalizeWindow(window, DEFAULT_RANGE_WINDOW);
        String matchers = matchersFor(key, uri, method);
        String promql = key.toPromql(matchers, effectiveWindow);

        Instant end = TimeParser.parse(endTime, Instant.now());
        Instant start = TimeParser.parse(startTime, end.minus(Duration.ofHours(1)));
        if (!start.isBefore(end)) {
            throw new IllegalArgumentException("start 必须早于 end");
        }

        Duration step = parseStep(stepText);
        long points = Duration.between(start, end).getSeconds() / step.getSeconds() + 1;
        if (points > MAX_RANGE_POINTS) {
            throw new IllegalArgumentException(
                    "区间过大：按当前 step 约 " + points + " 个点，最大 " + MAX_RANGE_POINTS);
        }

        PrometheusResponse response = client.queryRange(promql, start, end, step);
        return toMetricRange(key, uri, method, start, end, step, response);
    }

    /**
     * 对比当前值与基线时间点的值，按偏差排序。
     */
    public DeviationResult queryTopDeviation(String metric, String baseline, String window,
                                             Integer topN, Double minDeviation) {
        MetricKey key = MetricKey.from(metric);
        String effectiveWindow = normalizeWindow(window, properties.getDefaultEndpointWindow());
        String matchers = key.isEndpointScoped()
                ? baseMatchers() + ACTUATOR_EXCLUDE
                : baseMatchers();
        String promql = key.toGroupedPromql(matchers, effectiveWindow);

        String baselineLabel = normalizeBaseline(baseline);
        Duration baselineDuration = parseBaseline(baselineLabel);
        int limit = topN == null || topN <= 0 ? DEFAULT_TOP_N : Math.min(topN, MAX_TOP_N);
        double threshold = minDeviation == null || minDeviation < 0 ? 0.0 : minDeviation;

        Instant now = Instant.now();
        Instant baselineAt = now.minus(baselineDuration);
        Map<String, Sample> current = parseSamples(client.query(promql));
        Map<String, Sample> previous = parseSamples(client.queryAt(promql, baselineAt));

        LinkedHashSet<String> seriesIds = new LinkedHashSet<>();
        seriesIds.addAll(current.keySet());
        seriesIds.addAll(previous.keySet());

        List<DeviationItem> items = new ArrayList<>();
        for (String seriesId : seriesIds) {
            Sample currentSample = current.get(seriesId);
            Sample baselineSample = previous.get(seriesId);
            double currentValue = currentSample == null ? 0.0 : currentSample.value();
            double baselineValue = baselineSample == null ? 0.0 : baselineSample.value();
            double absoluteDelta = currentValue - baselineValue;
            Double relativeDelta = Math.abs(baselineValue) > 1e-9
                    ? absoluteDelta / Math.abs(baselineValue)
                    : null;
            double rankValue = relativeDelta == null
                    ? Math.abs(absoluteDelta)
                    : Math.abs(relativeDelta);
            if (rankValue <= 0 || rankValue < threshold) {
                continue;
            }
            String direction = relativeDelta == null
                    ? (currentValue > 0 ? "NEW" : "FLAT")
                    : (absoluteDelta > 0 ? "UP" : "DOWN");
            Map<String, String> labels = currentSample != null
                    ? currentSample.labels()
                    : baselineSample.labels();
            items.add(new DeviationItem(seriesId, labels, currentValue, baselineValue,
                    absoluteDelta, relativeDelta, direction,
                    relativeDelta == null ? "absolute" : "relative", rankValue));
        }

        items.sort(Comparator.comparingDouble(DeviationItem::rankValue).reversed());
        if (items.size() > limit) {
            items = new ArrayList<>(items.subList(0, limit));
        }

        return new DeviationResult(key.getKey(), key.getUnit(), effectiveWindow, baselineLabel,
                baselineAt, threshold, items, now);
    }

    private Map<String, Sample> parseSamples(PrometheusResponse response) {
        Map<String, Sample> samples = new LinkedHashMap<>();
        if (response.data() == null || response.data().result() == null) {
            return samples;
        }
        for (PrometheusResponse.Result result : response.data().result()) {
            Double value = valueOf(result.value());
            if (value == null) {
                continue;
            }
            Map<String, String> labels = result.metric() == null ? Map.of() : result.metric();
            samples.put(seriesKey(labels), new Sample(labels, value));
        }
        return samples;
    }

    private String seriesKey(Map<String, String> labels) {
        if (labels == null || labels.isEmpty()) {
            return "";
        }
        String uri = labels.get("uri");
        if (uri != null && !uri.isEmpty()) {
            return uri + "|" + labels.getOrDefault("method", "");
        }
        return labels.toString();
    }

    private Double valueOf(List<Object> value) {
        if (value == null || value.size() < 2) {
            return null;
        }
        try {
            double parsed = Double.parseDouble(String.valueOf(value.get(1)));
            return Double.isFinite(parsed) ? parsed : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String normalizeBaseline(String baseline) {
        return baseline == null || baseline.isBlank()
                ? DEFAULT_BASELINE
                : baseline.trim().toLowerCase(Locale.ROOT);
    }

    private Duration parseBaseline(String baseline) {
        return switch (baseline) {
            case "30m" -> Duration.ofMinutes(30);
            case "1h" -> Duration.ofHours(1);
            case "6h" -> Duration.ofHours(6);
            case "12h" -> Duration.ofHours(12);
            case "1d" -> Duration.ofDays(1);
            case "7d" -> Duration.ofDays(7);
            default -> throw new IllegalArgumentException(
                    "非法 baseline: " + baseline + "，可选: 30m/1h/6h/12h/1d/7d");
        };
    }

    private record Sample(Map<String, String> labels, Double value) {
    }

    private String matchersFor(MetricKey key, String uri, String method) {
        String base = baseMatchers();
        if (!key.isEndpointScoped()) {
            return base;
        }
        if (uri != null && !uri.isBlank()) {
            return PromqlTemplates.endpointMatchers(base, uri.trim(), method);
        }
        return base + ACTUATOR_EXCLUDE;
    }

    private List<MetricKey> parseMetricKeys(String metrics) {
        if (metrics == null || metrics.isBlank()) {
            throw new IllegalArgumentException("metrics 不能为空，可选: " + MetricKey.supportedKeys());
        }
        LinkedHashSet<MetricKey> keys = new LinkedHashSet<>();
        for (String raw : metrics.split(",")) {
            if (raw.isBlank()) {
                continue;
            }
            keys.add(MetricKey.from(raw));
            if (keys.size() > MAX_BATCH_METRICS) {
                throw new IllegalArgumentException("单次最多查询 " + MAX_BATCH_METRICS + " 个指标");
            }
        }
        if (keys.isEmpty()) {
            throw new IllegalArgumentException("metrics 不能为空，可选: " + MetricKey.supportedKeys());
        }
        return new ArrayList<>(keys);
    }

    private MetricRange toMetricRange(MetricKey key, String uri, String method,
                                      Instant start, Instant end, Duration step,
                                      PrometheusResponse response) {
        List<MetricRange.Series> series = new ArrayList<>();
        int pointCount = 0;
        boolean truncated = false;

        if (response.data() != null && response.data().result() != null) {
            for (PrometheusResponse.Result result : response.data().result()) {
                if (series.size() >= MAX_RANGE_SERIES) {
                    truncated = true;
                    break;
                }
                List<MetricRange.Point> points = new ArrayList<>();
                if (result.values() != null) {
                    for (List<Object> value : result.values()) {
                        MetricRange.Point point = toPoint(value);
                        if (point != null) {
                            points.add(point);
                        }
                    }
                } else if (result.value() != null) {
                    MetricRange.Point point = toPoint(result.value());
                    if (point != null) {
                        points.add(point);
                    }
                }
                pointCount += points.size();
                series.add(new MetricRange.Series(result.metric(), points));
            }
        }

        return new MetricRange(key.getKey(), key.getUnit(), uri, method, start, end,
                step.getSeconds() + "s", series, pointCount, truncated);
    }

    private MetricRange.Point toPoint(List<Object> value) {
        if (value == null || value.size() < 2) {
            return null;
        }
        try {
            double epochSeconds = Double.parseDouble(String.valueOf(value.get(0)));
            double parsed = Double.parseDouble(String.valueOf(value.get(1)));
            if (!Double.isFinite(parsed)) {
                return null;
            }
            return new MetricRange.Point(
                    Instant.ofEpochMilli(Math.round(epochSeconds * 1000)), parsed);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Duration parseStep(String text) {
        String value = text == null || text.isBlank() ? properties.getDefaultStep() : text.trim();
        Matcher matcher = STEP_PATTERN.matcher(value.toLowerCase(Locale.ROOT));
        if (matcher.matches()) {
            long amount = Long.parseLong(matcher.group(1));
            if (amount <= 0) {
                throw new IllegalArgumentException("step 必须大于 0");
            }
            return switch (matcher.group(2)) {
                case "s" -> Duration.ofSeconds(amount);
                case "m" -> Duration.ofMinutes(amount);
                case "h" -> Duration.ofHours(amount);
                case "d" -> Duration.ofDays(amount);
                default -> throw new IllegalArgumentException("非法 step: " + text);
            };
        }
        try {
            long seconds = Long.parseLong(value);
            if (seconds <= 0) {
                throw new IllegalArgumentException("step 必须大于 0");
            }
            return Duration.ofSeconds(seconds);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("非法 step: " + text + "，示例: 15s/30s/1m/5m");
        }
    }

    private String baseMatchers() {
        return PromqlTemplates.matchers(
                properties.getJob(), properties.getApplication(), properties.getEnv());
    }

    private String normalizeWindow(String window, String defaultWindow) {
        return window == null || window.isBlank() ? defaultWindow : window.trim();
    }

    private Double firstValue(PrometheusResponse response) {
        if (response.data() == null
                || response.data().result() == null
                || response.data().result().isEmpty()) {
            return null;
        }
        return valueOf(response.data().result().get(0).value());
    }
}
