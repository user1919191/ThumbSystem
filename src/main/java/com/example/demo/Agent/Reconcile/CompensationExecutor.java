package com.example.demo.Agent.Reconcile;

import com.example.demo.Agent.Service.ToolsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class CompensationExecutor {
    private final ToolsService tools;
    private final Set<String> completedKeys = ConcurrentHashMap.newKeySet();

    public ExecutionResult execute(CompensationPlan plan) {
        long started = System.currentTimeMillis();
        if (plan == null)
            return failed(null, "PLAN_MISSING", "Compensation plan is required", started);
        if (!"READY".equals(plan.status()) || !plan.dryRun())
            return failed(plan, "PLAN_NOT_READY", "A successful dry-run is required", started);
        if (completedKeys.contains(plan.idempotencyKey()))
            return result(plan, "SKIPPED", null, null,
                    Map.of("reason", "idempotency key already completed"), started);
        try {
            Long userId = longParam(plan.params(), "userId");
            Long blogId = longParam(plan.params(), "blogId");
            Map<String, Object> toolResult = switch (plan.action()) {
                case "WRITE_DB" -> tools.insertDbThumb(userId, blogId, false);
                case "REBUILD_REDIS" -> tools.insertRedisThumb(userId, blogId, false);
                case "DELETE_REDIS" -> tools.deleteRedisThumb(userId, blogId, false);
                case "RECOMPUTE_COUNT" -> tools.recomputeDbThumbCount(blogId, false);
                default -> throw new IllegalArgumentException("Unsupported action: " + plan.action());
            };
            String status = String.valueOf(toolResult.getOrDefault("status", "FAILED"));
            if ("EXECUTED".equals(status) || "SKIPPED".equals(status)) {
                completedKeys.add(plan.idempotencyKey());
                return result(plan, status, null, null, toolResult, started);
            }
            return failed(plan, "TOOL_EXECUTION_FAILED",
                    String.valueOf(toolResult.getOrDefault("message", "tool failed")), toolResult, started);
        } catch (RuntimeException e) {
            return failed(plan, "EXECUTION_EXCEPTION",
                    e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage(), started);
        }
    }

    private Long longParam(Map<String, Object> params, String key) {
        Object value = params.get(key);
        if (value instanceof Number number) return number.longValue();
        if (value == null) throw new IllegalArgumentException("Missing parameter: " + key);
        return Long.valueOf(String.valueOf(value));
    }
    private ExecutionResult failed(CompensationPlan plan, String code, String message, long started) {
        return failed(plan, code, message, Map.of(), started);
    }
    private ExecutionResult failed(CompensationPlan plan, String code, String message,
                                   Map<String, Object> detail, long started) {
        return result(plan, "FAILED", code, message, detail, started);
    }
    private ExecutionResult result(CompensationPlan plan, String status, String code,
                                   String message, Map<String, Object> detail, long started) {
        return new ExecutionResult(plan == null ? null : plan.planId(),
                plan == null ? null : plan.evidenceId(), plan == null ? null : plan.action(),
                status, code, message, detail,
                "EXECUTED".equals(status) ? "PENDING_NEXT_RECONCILE" : "NOT_APPLICABLE",
                System.currentTimeMillis() - started);
    }
}
