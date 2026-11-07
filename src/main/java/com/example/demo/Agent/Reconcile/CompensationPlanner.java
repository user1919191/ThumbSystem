package com.example.demo.Agent.Reconcile;

import com.example.demo.Agent.Service.ToolsService;
import com.example.demo.compensation.ReconcileDiffItem;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CompensationPlanner {
    private static final Set<String> EXECUTABLE = Set.of(
            "WRITE_DB", "REBUILD_REDIS", "DELETE_REDIS", "RECOMPUTE_COUNT");

    private final ToolsService tools;

    public CompensationPlan plan(ReconcileDiffItem item, EvidenceSnapshot snapshot,
                                 AgentDiagnosis diagnosis,
                                 DiagnosisValidator.ValidationResult validation) {
        if (validation == null || !validation.passed()) {
            return nonExecutable(item, snapshot, diagnosis, "ESCALATE", "HIGH",
                    "INVALID", "validation did not pass");
        }
        String action = diagnosis.proposedAction();
        if ("SKIP".equals(action) || "ESCALATE".equals(action)) {
            return nonExecutable(item, snapshot, diagnosis, action, "NONE",
                    action, "No write action required");
        }
        if (!EXECUTABLE.contains(action)) {
            return nonExecutable(item, snapshot, diagnosis, "ESCALATE", "HIGH",
                    "INVALID", "unsupported action");
        }

        Map<String, Object> params = Map.of("userId", item.userId(), "blogId", item.blogId());
        Map<String, Object> preview = dryRun(action, item.userId(), item.blogId());
        String dryRunStatus = string(preview.get("status"));
        String planStatus = switch (dryRunStatus == null ? "" : dryRunStatus) {
            case "PLANNED" -> "READY";
            case "SKIPPED" -> "SKIP";
            default -> "INVALID";
        };
        String risk = risk(action, diagnosis.classification());
        return new CompensationPlan(planId(), snapshot.evidenceId(), snapshot.diffKey(),
                validation.finalClassification(), validation.finalConfidence(), diagnosis.needHuman(),
                action, params, true, risk, sha1(snapshot.diffKey() + '|' + action),
                expectedEffects(action),
                new CompensationPlan.DryRunResult(dryRunStatus, string(preview.get("message")), preview),
                ruleFallback(diagnosis) ? "RULE_FALLBACK" : "LLM", planStatus);
    }

    private Map<String, Object> dryRun(String action, Long userId, Long blogId) {
        try {
            return switch (action) {
                case "WRITE_DB" -> tools.insertDbThumb(userId, blogId, true);
                case "REBUILD_REDIS" -> tools.insertRedisThumb(userId, blogId, true);
                case "DELETE_REDIS" -> tools.deleteRedisThumb(userId, blogId, true);
                case "RECOMPUTE_COUNT" -> tools.recomputeDbThumbCount(blogId, true);
                default -> Map.of("status", "FAILED", "message", "unsupported action");
            };
        } catch (RuntimeException e) {
            return Map.of("status", "FAILED", "message", safeMessage(e));
        }
    }

    private CompensationPlan nonExecutable(ReconcileDiffItem item, EvidenceSnapshot snapshot,
                                           AgentDiagnosis diagnosis, String action, String risk,
                                           String status, String message) {
        String classification = diagnosis == null ? "A4" : diagnosis.classification();
        double confidence = diagnosis == null ? 0 : diagnosis.confidence();
        boolean needHuman = diagnosis == null || diagnosis.needHuman();
        String diffKey = snapshot == null ? item.diffKey() : snapshot.diffKey();
        String evidenceId = snapshot == null ? null : snapshot.evidenceId();
        return new CompensationPlan(planId(), evidenceId, diffKey, classification, confidence,
                needHuman, action, Map.of("userId", item.userId(), "blogId", item.blogId()),
                true, risk, sha1(diffKey + '|' + action), List.of(),
                new CompensationPlan.DryRunResult(status, message, Map.of()),
                ruleFallback(diagnosis) ? "RULE_FALLBACK" : "LLM", status);
    }

    private String risk(String action, String classification) {
        if ("DELETE_REDIS".equals(action) || "A7".equals(classification)) return "HIGH";
        return "WRITE_DB".equals(action) ? "MEDIUM" : "LOW";
    }
    private List<String> expectedEffects(String action) {
        return switch (action) {
            case "WRITE_DB" -> List.of("thumb detail exists", "blog.thumbCount is recomputed");
            case "REBUILD_REDIS" -> List.of("Redis thumb state exists");
            case "DELETE_REDIS" -> List.of("Redis thumb state is absent");
            case "RECOMPUTE_COUNT" -> List.of("blog.thumbCount equals DB detail count");
            default -> List.of();
        };
    }
    private boolean ruleFallback(AgentDiagnosis diagnosis) {
        return diagnosis != null && diagnosis.trace() != null
                && "RULE_FALLBACK".equals(diagnosis.trace().parseStatus());
    }
    private String planId() { return "plan-" + UUID.randomUUID(); }
    private String string(Object value) { return value == null ? null : String.valueOf(value); }
    private String safeMessage(Exception e) { return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage(); }
    private String sha1(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-1")
                    .digest(String.valueOf(value).getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (byte current : bytes) result.append(String.format("%02x", current));
            return result.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Cannot create idempotency key", e);
        }
    }
}
