package com.example.demo.monitor.prometheus.Entry;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.Map;

/**
 * Prometheus /api/v1/query 响应结构。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PrometheusResponse(
        String status,
        Data data,
        String errorType,
        String error,
        List<String> warnings) {

    public boolean isSuccess() {
        return "success".equals(status);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Data(String resultType, List<Result> result) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Result(Map<String, String> metric, List<Object> value, List<List<Object>> values) {
    }
}
