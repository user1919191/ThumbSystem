package com.example.demo.monitor.prometheus.Config;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 固定 PromQL 模板，避免外部直接传入 PromQL 造成注入或任意查询。
 */
public final class PromqlTemplates {

    private static final Set<String> ALLOWED_METHODS = Set.of("GET", "POST", "PUT", "DELETE", "PATCH");
    private static final Set<String> ALLOWED_WINDOWS = Set.of("1m", "5m", "15m", "30m", "1h", "6h", "24h");
    private static final Pattern URI_PATTERN = Pattern.compile("^/[A-Za-z0-9/{}._~-]*$");

    private PromqlTemplates() {
    }

    public static String matchers(String job, String application, String env) {
        return "job=\"" + escape(job) + "\",application=\"" + escape(application)
                + "\",env=\"" + escape(env) + "\"";
    }

    public static String endpointMatchers(String baseMatchers, String uri, String method) {
        if (uri == null || !URI_PATTERN.matcher(uri).matches()) {
            throw new IllegalArgumentException("非法 uri: " + uri);
        }
        String normalizedMethod = method == null ? "" : method.trim().toUpperCase(Locale.ROOT);
        if (!ALLOWED_METHODS.contains(normalizedMethod)) {
            throw new IllegalArgumentException("非法 method: " + method);
        }
        return baseMatchers + ",uri=\"" + escape(uri) + "\",method=\"" + normalizedMethod + "\"";
    }

    public static String qps(String matchers, String window) {
        validateWindow(window);
        return "sum(rate(http_server_requests_seconds_count{" + matchers + "}[" + window + "]))";
    }

    public static String successRate(String matchers, String window) {
        validateWindow(window);
        String successMatchers = matchers + ",status!~\"5..\"";
        return "sum(rate(http_server_requests_seconds_count{" + successMatchers + "}[" + window + "]))"
                + " / clamp_min(sum(rate(http_server_requests_seconds_count{"
                + matchers + "}[" + window + "])), 1e-9)";
    }

    public static String p99(String matchers, String window) {
        validateWindow(window);
        return "histogram_quantile(0.99, sum by (le) (rate(http_server_requests_seconds_bucket{"
                + matchers + "}[" + window + "])))";
    }

    public static String totalRequests(String matchers) {
        return "sum(http_server_requests_seconds_count{" + matchers + "})";
    }

    private static void validateWindow(String window) {
        if (!ALLOWED_WINDOWS.contains(window)) {
            throw new IllegalArgumentException("非法 window: " + window);
        }
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n");
    }
}
