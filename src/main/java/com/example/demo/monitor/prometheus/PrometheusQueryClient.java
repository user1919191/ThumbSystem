package com.example.demo.monitor.prometheus;

import com.example.demo.monitor.prometheus.Config.PrometheusProperties;
import com.example.demo.monitor.prometheus.Entry.PrometheusResponse;
import com.example.demo.monitor.prometheus.Exception.PrometheusQueryException;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;

/**
 * Prometheus HTTP API 客户端
 */
@Component
public class PrometheusQueryClient {

    private final RestClient restClient;
    private final PrometheusProperties properties;

    public PrometheusQueryClient(RestClient.Builder builder, PrometheusProperties properties) {
        this.properties = properties;
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder()
                        .connectTimeout(properties.getTimeout())
                        .build());
        requestFactory.setReadTimeout(properties.getTimeout());
        this.restClient = builder
                .baseUrl(properties.getBaseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    /**
     * 执行 PromQL 瞬时查询。
     */
    public PrometheusResponse query(String promql) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("query", promql);
        form.add("timeout", properties.getTimeout().toMillis() + "ms");
        return execute("/api/v1/query", form);
    }

    /**
     * 在指定时间点执行 PromQL，用于同比/基线对比。
     */
    public PrometheusResponse queryAt(String promql, Instant time) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("query", promql);
        form.add("time", String.valueOf(time.getEpochSecond()));
        form.add("timeout", properties.getTimeout().toMillis() + "ms");
        return execute("/api/v1/query", form);
    }

    /**
     * 执行 PromQL 区间查询。
     */
    public PrometheusResponse queryRange(String promql, Instant start, Instant end, Duration step) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("query", promql);
        form.add("start", String.valueOf(start.getEpochSecond()));
        form.add("end", String.valueOf(end.getEpochSecond()));
        form.add("step", Math.max(1, step.getSeconds()) + "s");
        form.add("timeout", properties.getTimeout().toMillis() + "ms");
        return execute("/api/v1/query_range", form);
    }

    private PrometheusResponse execute(String path, MultiValueMap<String, String> form) {
        try {
            PrometheusResponse response = restClient.post()
                    .uri(path)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(PrometheusResponse.class);

            if (response == null) {
                throw new PrometheusQueryException("Prometheus 返回空响应");
            }
            if (!response.isSuccess()) {
                throw new PrometheusQueryException(
                        "Prometheus 查询失败: " + response.errorType() + " - " + response.error());
            }
            return response;
        } catch (RestClientResponseException e) {
            throw new PrometheusQueryException(
                    "Prometheus HTTP " + e.getStatusCode().value() + ": " + e.getResponseBodyAsString(), e);
        } catch (ResourceAccessException e) {
            throw new PrometheusQueryException("Prometheus 连接失败: " + properties.getBaseUrl(), e);
        }
    }
}
