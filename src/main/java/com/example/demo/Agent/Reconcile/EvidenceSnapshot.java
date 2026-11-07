package com.example.demo.Agent.Reconcile;

import com.example.demo.compensation.ReconcileDiffType;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Immutable, read-only evidence collected before any diagnosis or action. */
public record EvidenceSnapshot(
        String evidenceId, String batchNo, String diffKey, ReconcileDiffType diffType,
        Long userId, Long blogId, RedisEvidence redis, DbEvidence db, DlqEvidence dlq,
        RetryTaskEvidence retryTask, LogEvidence logs, boolean complete,
        List<String> missingSources, String evidenceHash, Instant collectedAt,
        long collectCostMs) {

    public record RedisEvidence(boolean available, boolean hasThumb, String value,
                                Long ttlSeconds, long latencyMs) {}

    public record DbEvidence(boolean available, boolean thumbExists, Long thumbId,
                             Long blogThumbCount, Long detailCount, long latencyMs) {}

    public record DlqEvidence(boolean available, boolean matched, String messageId,
                             String eventId, String payload, String publishTime,
                             Map<String, String> properties, int scanned, long latencyMs) {}

    public record RetryTaskEvidence(boolean available, boolean exists, String taskNo,
                                    String status, Integer attempt, String lastError,
                                    long latencyMs) {}

    public record LogEvidence(boolean available, String traceId, String eventId,
                               String stage, String lastError, List<String> samples,
                               long latencyMs) {}
}
