package com.example.demo.Agent.Service;

import java.util.Map;

public interface ToolsService {

    Map<String, Object> queryRetryTasks(String eventId, String bizKey, String status, Integer limit);
    Map<String, Object> queryPulsarMessages(String topic, Integer limit);
    Map<String, Object> queryDlqMessages(Integer limit);
    Map<String, Object> queryJvmGcQpsStatus();
}
