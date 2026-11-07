package com.example.demo.Agent.Reconcile;

import java.time.Instant;

public record AgentRoundTrace(int round, String evidenceId, String evidenceHash,
                              String classification, double confidence,
                              String lowConfidenceReason, String validationStatus,
                              String validationReason, String rawOutput, Instant createdAt) {
}
