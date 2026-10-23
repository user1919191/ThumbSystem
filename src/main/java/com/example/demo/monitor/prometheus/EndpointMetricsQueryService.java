package com.example.demo.monitor.prometheus;

import com.example.demo.monitor.prometheus.Config.PrometheusProperties;
import com.example.demo.monitor.prometheus.Config.PromqlTemplates;
import com.example.demo.monitor.prometheus.Entry.EndpointMetrics;
import com.example.demo.monitor.prometheus.Entry.OverviewMetrics;
import com.example.demo.monitor.prometheus.Entry.PrometheusResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * 通过 Prometheus HTTP API 查询接口 QPS、成功率和 P99。
 */
@Service
@RequiredArgsConstructor
public class EndpointMetricsQueryService {

    private static final String ACTUATOR_EXCLUDE = ",uri!~\"/(api/)?actuator.*\"";

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
        List<Object> value = response.data().result().get(0).value();
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
}
