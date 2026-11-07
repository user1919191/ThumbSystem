package com.example.demo.Agent.Reconcile;

import com.example.demo.compensation.ReconcileDiffItem;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Serializable state carried between reconciliation graph nodes. */
public class ReconcileGraphState {
    public String graphRunId;
    public String threadId;
    public String batchNo;
    public int sequence;
    public ReconcileDiffItem item;
    public EvidenceSnapshot snapshot;
    public AgentDiagnosis diagnosis;
    public DiagnosisValidator.ValidationResult validation;
    public CompensationPlan plan;
    public CompensationApprovalService.ApprovalRequirement approvalRequirement;
    public CompensationApprovalService.ApprovalSubmission approvalSubmission;
    public ExecutionResult execution;
    public String outcome = "RUNNING";
    public String failureCode;
    public String failureMessage;
    public int retryCount;
    public int agentRound;
    public String lowConfidenceReason;
    public String evidenceRequest;
    public String currentNode;
    public Instant startedAt = Instant.now();
    public Instant updatedAt = Instant.now();
    public final List<AgentRoundTrace> rounds = new ArrayList<>();

    public ReconcileGraphState(String batchNo, int sequence, ReconcileDiffItem item) {
        this.batchNo = batchNo;
        this.sequence = sequence;
        this.item = item;
        this.graphRunId = "gr-" + java.util.UUID.randomUUID();
        this.threadId = batchNo + ":" + (item == null ? sequence : item.diffKey());
    }

    public void enter(String node) {
        this.currentNode = node;
        this.updatedAt = Instant.now();
    }
}
