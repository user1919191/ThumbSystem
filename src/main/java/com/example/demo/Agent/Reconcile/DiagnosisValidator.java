package com.example.demo.Agent.Reconcile;

import com.example.demo.compensation.ReconcileDiffItem;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;

@Service
public class DiagnosisValidator {
    private static final Set<String> CLASSES = Set.of("A1","A2","A3","A4","A5","A6","A7","A8");
    private static final Set<String> ACTIONS = Set.of("WRITE_DB","REBUILD_REDIS","DELETE_REDIS",
            "RECOMPUTE_COUNT","SKIP","ESCALATE");
    private static final Set<String> FIELDS = Set.of(
            "redis.available","redis.hasThumb","redis.value","redis.ttlSeconds",
            "db.available","db.thumbExists","db.thumbId","db.blogThumbCount","db.detailCount",
            "dlq.available","dlq.matched","dlq.messageId","dlq.eventId","dlq.payload",
            "retryTask.available","retryTask.exists","retryTask.taskNo","retryTask.status",
            "retryTask.attempt","retryTask.lastError","logs.available","logs.traceId",
            "logs.eventId","logs.stage","logs.lastError");

    public ValidationResult validate(ReconcileDiffItem item, EvidenceSnapshot snapshot,
                                     AgentDiagnosis diagnosis) {
        if (snapshot == null || diagnosis == null) return invalid("INVALID_DIAGNOSIS", "diagnosis or snapshot is null");
        if (!same(diagnosis.evidenceId(), snapshot.evidenceId())) return invalid("INVALID_DIAGNOSIS", "evidenceId mismatch");
        if (!CLASSES.contains(diagnosis.classification()) || Double.isNaN(diagnosis.confidence())
                || diagnosis.confidence() < 0 || diagnosis.confidence() > 1)
            return invalid("INVALID_DIAGNOSIS", "classification or confidence is invalid");
        if (!ACTIONS.contains(diagnosis.proposedAction())) return invalid("ACTION_NOT_ALLOWED", "action is not whitelisted");
        if (!actionMatchesClassification(diagnosis.classification(), diagnosis.proposedAction()))
            return invalid("ACTION_NOT_ALLOWED", "action conflicts with classification");
        if (diagnosis.evidenceRefs() == null || diagnosis.evidenceRefs().isEmpty())
            return invalid("CITATION_INVALID", "evidenceRefs is empty");
        for (AgentDiagnosis.EvidenceRef ref : diagnosis.evidenceRefs()) {
            if (ref == null || !same(ref.evidenceId(), snapshot.evidenceId()) || !FIELDS.contains(ref.field()))
                return invalid("CITATION_INVALID", "invalid evidence reference");
            Object actual = field(snapshot, ref.field());
            if (actual == MISSING || !valueMatches(ref.value(), actual))
                return invalid("CITATION_INVALID", "evidence reference value does not match snapshot: " + ref.field());
        }
        String conflict = conflict(snapshot, diagnosis.classification());
        if (conflict != null) return invalid("EVIDENCE_CONFLICT", conflict);
        return new ValidationResult(true, "PASSED", "ok", diagnosis.classification(), diagnosis.confidence(), false);
    }

    private String conflict(EvidenceSnapshot s, String c) {
        if (!s.redis().available() || !s.db().available()) return "A4".equals(c) || "A8".equals(c) ? null : "key evidence unavailable";
        if (("A2".equals(c) && s.redis().hasThumb() != s.db().thumbExists())
                || (("A3".equals(c) || "A5".equals(c)) && (!s.redis().hasThumb() || s.db().thumbExists()))
                || ("A6".equals(c) && (!s.db().thumbExists() || s.redis().hasThumb()))) return "classification conflicts with Redis/DB state";
        return null;
    }
    private boolean actionMatchesClassification(String classification, String action) {
        if ("RECOMPUTE_COUNT".equals(action)) return true;
        return switch (classification) {
            case "A1" -> Set.of("DELETE_REDIS", "ESCALATE", "SKIP").contains(action);
            case "A2" -> "SKIP".equals(action);
            case "A3", "A5" -> Set.of("WRITE_DB", "ESCALATE").contains(action);
            case "A4", "A8" -> "ESCALATE".equals(action);
            case "A6" -> Set.of("REBUILD_REDIS", "ESCALATE").contains(action);
            case "A7" -> Set.of("DELETE_REDIS", "ESCALATE").contains(action);
            default -> false;
        };
    }
    private static final Object MISSING = new Object();
    private Object field(EvidenceSnapshot s, String f) {
        return switch (f) {
            case "redis.available" -> s.redis().available(); case "redis.hasThumb" -> s.redis().hasThumb(); case "redis.value" -> s.redis().value(); case "redis.ttlSeconds" -> s.redis().ttlSeconds();
            case "db.available" -> s.db().available(); case "db.thumbExists" -> s.db().thumbExists(); case "db.thumbId" -> s.db().thumbId(); case "db.blogThumbCount" -> s.db().blogThumbCount(); case "db.detailCount" -> s.db().detailCount();
            case "dlq.available" -> s.dlq().available(); case "dlq.matched" -> s.dlq().matched(); case "dlq.messageId" -> s.dlq().messageId(); case "dlq.eventId" -> s.dlq().eventId(); case "dlq.payload" -> s.dlq().payload();
            case "retryTask.available" -> s.retryTask().available(); case "retryTask.exists" -> s.retryTask().exists(); case "retryTask.taskNo" -> s.retryTask().taskNo(); case "retryTask.status" -> s.retryTask().status(); case "retryTask.attempt" -> s.retryTask().attempt(); case "retryTask.lastError" -> s.retryTask().lastError();
            case "logs.available" -> s.logs().available(); case "logs.traceId" -> s.logs().traceId(); case "logs.eventId" -> s.logs().eventId(); case "logs.stage" -> s.logs().stage(); case "logs.lastError" -> s.logs().lastError(); default -> MISSING;
        };
    }
    private boolean valueMatches(String expected, Object actual) { return expected == null ? actual == null : actual != null && expected.equals(String.valueOf(actual)); }
    private boolean same(String a, String b) { return a != null && a.equals(b); }
    private ValidationResult invalid(String status, String reason) { return new ValidationResult(false, status, reason, "A4", 0D, true); }
    public record ValidationResult(boolean passed, String status, String reason,
                                   String finalClassification, Double finalConfidence,
                                   boolean fallbackFromRules) {}
}
