package com.example.demo.Agent.Reconcile;

import java.util.List;
import java.util.Map;

public record CompensationPlan(
        String planId,
        String evidenceId,
        String diffKey,
        String classification,
        double confidence,
        boolean needHuman,
        String action,
        Map<String, Object> params,
        boolean dryRun,
        String risk,
        String idempotencyKey,
        List<String> expectedEffects,
        DryRunResult dryRunResult,
        String source,
        String status) {

    public record DryRunResult(String status, String message, Map<String, Object> preview) {
    }
}
