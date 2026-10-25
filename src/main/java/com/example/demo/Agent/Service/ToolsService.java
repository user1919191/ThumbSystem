package com.example.demo.Agent.Service;

import java.util.Map;

public interface ToolsService {

    // 服务 JVM/GC/QPS/P99 监控工具
    Map<String, Object> queryRetryTasks(String eventId, String bizKey, String status, Integer limit);
    Map<String, Object> queryPulsarMessages(String topic, Integer limit);
    Map<String, Object> queryDlqMessages(Integer limit);
    Map<String, Object> queryJvmGcStatus();
    Map<String, Object> queryQpsStatus();
    Map<String, Object> queryEndpointMetrics(String uri, String method, String window);
    Map<String, Object> queryMetricsBatch(String metrics, String uri, String method, String window);
    Map<String, Object> queryMetricsRange(String metric, String uri, String method,
                                          String startTime, String endTime, String step, String window);
    Map<String, Object> queryTopDeviation(String metric, String baseline, String window,
                                          Integer topN, Double minDeviation);
    Map<String, Object> queryLogs(String keyword, String level,
                                  String startTime, String endTime, Integer limit);

    // 对账证据与 Redis / DB 读写工具
    Map<String, Object> queryThumbEvidence(Long userId, Long blogId);
    Map<String, Object> queryRedisInfo(String key);
    Map<String, Object> queryDBInfo(String key);
    Map<String, Object> insertRedisThumb(Long userId, Long blogId, Boolean dryRun);
    Map<String, Object> deleteRedisThumb(Long userId, Long blogId, Boolean dryRun);
    Map<String, Object> insertDbThumb(Long userId, Long blogId, Boolean dryRun);
    Map<String, Object> deleteDbThumb(Long userId, Long blogId, Boolean dryRun);
}
