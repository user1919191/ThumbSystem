package com.example.demo.Agent.Reconcile;

import com.example.demo.Agent.Service.ToolsService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.Map;

/** Explicit read-only tool boundary for the reconciliation LLM. */
@Component
@RequiredArgsConstructor
public class ReconcileReadTools {
    private final ToolsService tools;

    @Tool(description = "Read Redis and DB thumb evidence for one user/blog pair. Read-only.")
    public Map<String,Object> queryThumbEvidence(@ToolParam(description = "User ID") Long userId,
                                                  @ToolParam(description = "Blog ID") Long blogId) {
        return tools.queryThumbEvidence(userId, blogId);
    }

    @Tool(description = "Read DLQ messages for correlation. Does not acknowledge messages.")
    public Map<String,Object> queryDlqMessages(@ToolParam(description = "Maximum messages, at most 100") Integer limit) {
        return tools.queryDlqMessages(limit);
    }

    @Tool(description = "Read retry tasks by event or business key. Read-only.")
    public Map<String,Object> queryRetryTasks(@ToolParam(description = "Event ID") String eventId,
                                              @ToolParam(description = "Business key") String bizKey,
                                              @ToolParam(description = "Task status") String status,
                                              @ToolParam(description = "Maximum rows") Integer limit) {
        return tools.queryRetryTasks(eventId, bizKey, status, limit);
    }

    @Tool(description = "Search recent application logs. Read-only and evidence only.")
    public Map<String,Object> queryLogs(@ToolParam(description = "Keyword") String keyword,
                                        @ToolParam(description = "Log level") String level,
                                        @ToolParam(description = "Start time") String startTime,
                                        @ToolParam(description = "End time") String endTime,
                                        @ToolParam(description = "Maximum results") Integer limit) {
        return tools.queryLogs(keyword, level, startTime, endTime, limit);
    }
}
