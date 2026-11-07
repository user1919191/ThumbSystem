package com.example.demo.Agent.Reconcile;

import java.util.List;
import java.util.Map;

public record AgentDiagnosis(String evidenceId, String classification, double confidence,
                             String summary, List<EvidenceRef> evidenceRefs,
                             String proposedAction, Map<String, Object> actionParams,
                             List<String> risks, boolean needHuman, AgentTrace trace) {
    public record EvidenceRef(String evidenceId, String field, String value, String meaning) {}
    public record AgentTrace(String model, String promptVersion, int rounds,
                             List<ToolCallRecord> toolCalls, int inputTokens,
                             int outputTokens, long costMs, String rawOutput,
                             String parseStatus) {}
    public record ToolCallRecord(int round, String toolName, Map<String, Object> args,
                                 String resultDigest, long latencyMs, boolean cached) {}
}
