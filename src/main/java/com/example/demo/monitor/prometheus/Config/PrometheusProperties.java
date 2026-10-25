package com.example.demo.monitor.prometheus.Config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Prometheus HTTP API 查询配置。
 */
@Component
@ConfigurationProperties(prefix = "prometheus")
@Getter
@Setter
public class PrometheusProperties {

    private String baseUrl = "http://localhost:9090";
    private Duration timeout = Duration.ofSeconds(3);
    private String job = "demo2";
    private String application = "demo2";
    private String env = "local";
    private String defaultQpsWindow = "1m";
    private String defaultSuccessWindow = "5m";
    private String defaultEndpointWindow = "5m";
    private String defaultStep = "15s";
}
