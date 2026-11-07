package com.example.demo.Agent.Reconcile;

import com.example.demo.mapper.ReconcileAgentRoundMapper;
import com.example.demo.mapper.ReconcileItemTraceMapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.demo.model.entity.ReconcileAgentRound;
import com.example.demo.model.entity.ReconcileItemTrace;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ReconcileTraceService {
    private final ReconcileItemTraceMapper itemMapper;
    private final ReconcileAgentRoundMapper roundMapper;
    private final ObjectMapper objectMapper;

    public void start(ReconcileGraphState state) {
        ReconcileItemTrace row = base(state);
        row.setTraceNo("trace-" + state.graphRunId);
        row.setOutcome("RUNNING");
        row.setStartedAt(LocalDateTime.now());
        row.setCreatedAt(LocalDateTime.now());
        itemMapper.insert(row);
    }

    public void recordRound(ReconcileGraphState state) {
        if (state.diagnosis == null) return;
        ReconcileAgentRound row = new ReconcileAgentRound();
        row.setGraphRunId(state.graphRunId);
        row.setThreadId(state.threadId);
        row.setRoundNo(state.agentRound);
        row.setEvidenceId(state.snapshot == null ? null : state.snapshot.evidenceId());
        row.setEvidenceHash(state.snapshot == null ? null : state.snapshot.evidenceHash());
        row.setClassification(state.diagnosis.classification());
        row.setConfidence(state.diagnosis.confidence());
        row.setLowConfidenceReason(state.lowConfidenceReason);
        row.setValidationStatus(state.validation == null ? null : state.validation.status());
        row.setValidationReason(state.validation == null ? null : state.validation.reason());
        row.setPromptVersion(state.diagnosis.trace() == null ? null : state.diagnosis.trace().promptVersion());
        row.setParseStatus(state.diagnosis.trace() == null ? null : state.diagnosis.trace().parseStatus());
        row.setRawOutput(state.diagnosis.trace() == null ? null : limit(state.diagnosis.trace().rawOutput(), 32768));
        row.setTraceJson(json(state.diagnosis.trace()));
        row.setCreatedAt(LocalDateTime.now());
        roundMapper.insert(row);
    }

    public void finish(ReconcileGraphState state) {
        ReconcileItemTrace row = base(state);
        row.setTraceNo("trace-" + state.graphRunId);
        row.setOutcome(state.outcome);
        row.setFailureCode(state.failureCode);
        row.setFailureMessage(limit(state.failureMessage, 512));
        row.setStartedAt(LocalDateTime.ofInstant(state.startedAt, java.time.ZoneId.systemDefault()));
        row.setFinishedAt(LocalDateTime.now());
        row.setCreatedAt(LocalDateTime.now());
        itemMapper.update(row, new LambdaUpdateWrapper<ReconcileItemTrace>()
                .eq(ReconcileItemTrace::getGraphRunId, state.graphRunId));
    }

    private ReconcileItemTrace base(ReconcileGraphState state) {
        ReconcileItemTrace row = new ReconcileItemTrace();
        row.setGraphRunId(state.graphRunId);
        row.setThreadId(state.threadId);
        row.setBatchNo(state.batchNo == null ? "UNKNOWN" : state.batchNo);
        row.setSequenceNo(state.sequence);
        if (state.item != null) {
            row.setDiffKey(state.item.diffKey() == null ? "INVALID:" + state.sequence : state.item.diffKey());
            row.setDiffType(state.item.diffType() == null ? "UNKNOWN" : state.item.diffType().name());
            row.setUserId(state.item.userId() == null ? 0L : state.item.userId());
            row.setBlogId(state.item.blogId() == null ? 0L : state.item.blogId());
        } else {
            row.setDiffKey("INVALID:" + state.sequence);
            row.setDiffType("UNKNOWN");
            row.setUserId(0L);
            row.setBlogId(0L);
        }
        row.setCurrentNode(state.currentNode == null ? "init" : state.currentNode);
        if (state.snapshot != null) {
            row.setEvidenceId(state.snapshot.evidenceId());
            row.setEvidenceHash(state.snapshot.evidenceHash());
        }
        if (state.diagnosis != null) {
            row.setClassification(state.diagnosis.classification());
            row.setConfidence(state.diagnosis.confidence());
        }
        if (state.validation != null) row.setValidationStatus(state.validation.status());
        if (state.plan != null) {
            row.setPlanId(state.plan.planId());
            row.setAction(state.plan.action());
            row.setIdempotencyKey(state.plan.idempotencyKey());
        }
        if (state.approvalSubmission != null) row.setApprovalNo(state.approvalSubmission.approvalNo());
        if (state.execution != null) {
            row.setExecutionStatus(state.execution.status());
            row.setConsistencyStatus(state.execution.consistencyStatus());
        }
        row.setStateJson(json(Map.of("agentRound", state.agentRound, "currentNode", state.currentNode == null ? "" : state.currentNode)));
        return row;
    }

    private String json(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (JsonProcessingException e) { return "{}"; }
    }
    private String limit(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max) + "...[truncated]";
    }
}
