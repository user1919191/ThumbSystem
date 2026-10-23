package com.example.demo.monitor.prometheus.Exception;

/**
 * Prometheus 查询失败异常。
 */
public class PrometheusQueryException extends RuntimeException {

    public PrometheusQueryException(String message) {
        super(message);
    }

    public PrometheusQueryException(String message, Throwable cause) {
        super(message, cause);
    }
}
