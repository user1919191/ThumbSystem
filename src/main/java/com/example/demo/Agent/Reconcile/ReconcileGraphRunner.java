package com.example.demo.Agent.Reconcile;

import com.example.demo.compensation.ReconcileDiffItem;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * LangGraph4J-compatible business graph runner. Nodes are explicit and state is
 * serializable; the external LangGraph4J dependency can replace this runner
 * without changing the node services or state contract.
 */
@Service
@RequiredArgsConstructor
public class ReconcileGraphRunner {
    private static final double CONFIDENCE_THRESHOLD = 0.8;
    private final EvidenceCollector evidenceCollector;
    private final ReconcileAgent reconcileAgent;
    private final DiagnosisValidator diagnosisValidator;
    private final CompensationPlanner compensationPlanner;
    private final CompensationApprovalService approvalService;
    private final CompensationExecutor compensationExecutor;
    private final ReconcileTraceService traceService;

    public GraphResult run(String batchNo, int sequence, ReconcileDiffItem item) {
        ReconcileGraphState state = new ReconcileGraphState(batchNo, sequence, item);
        try {
            state.enter("init");
            traceService.start(state);
            if (item == null || item.userId() == null || item.userId() <= 0
                    || item.blogId() == null || item.blogId() <= 0 || item.diffType() == null) {
                return fail(state, "INVALID_INPUT", "invalid reconcile item");
            }

            for (int round = 1; round <= 2; round++) {
                state.agentRound = round;
                state.enter("collect_evidence");
                state.snapshot = evidenceCollector.collect(item, batchNo, sequence);
                state.enter("diagnose");
                state.diagnosis = reconcileAgent.diagnose(item, state.snapshot);
                state.enter("validate_diagnosis");
                state.validation = diagnosisValidator.validate(item, state.snapshot, state.diagnosis);
                state.lowConfidenceReason = lowConfidenceReason(state);
                state.rounds.add(new AgentRoundTrace(round, state.snapshot.evidenceId(),
                        state.snapshot.evidenceHash(), state.diagnosis.classification(),
                        state.diagnosis.confidence(), state.lowConfidenceReason,
                        state.validation.status(), state.validation.reason(),
                        state.diagnosis.trace() == null ? null : state.diagnosis.trace().rawOutput(),
                        java.time.Instant.now()));
                traceService.recordRound(state);

                if (state.validation.passed() && state.diagnosis.confidence() >= CONFIDENCE_THRESHOLD) {
                    break;
                }
                if ("EVIDENCE_CONFLICT".equals(state.lowConfidenceReason)) {
                    return approval(state, "EVIDENCE_CONFLICT");
                }
                if ("EVIDENCE_INSUFFICIENT".equals(state.lowConfidenceReason) && round < 2) {
                    state.enter("request_more_evidence");
                    state.evidenceRequest = String.join(",", state.snapshot.missingSources());
                    continue;
                }
                return approval(state, round >= 2 ? "LOW_CONFIDENCE_AFTER_TWO_ROUNDS" : "LOW_CONFIDENCE");
            }

            state.enter("build_plan");
            state.plan = compensationPlanner.plan(item, state.snapshot, state.diagnosis, state.validation);
            state.enter("route_plan");
            CompensationApprovalService.ApprovalRequirement requirement =
                    approvalService.assess(state.plan, state.validation);
            state.approvalRequirement = requirement;
            if (requirement.required()) return approval(state, requirement.reason());
            if ("SKIP".equals(state.plan.action()) || "SKIP".equals(state.plan.status())) {
                state.outcome = "SKIPPED";
                return finish(state);
            }
            state.enter("execute");
            state.execution = compensationExecutor.execute(state.plan);
            state.outcome = "EXECUTED".equals(state.execution.status()) ? "SUCCESS"
                    : "SKIPPED".equals(state.execution.status()) ? "SKIPPED" : "FAILED";
            if ("FAILED".equals(state.outcome)) {
                state.failureCode = state.execution.errorCode();
                state.failureMessage = state.execution.errorMessage();
            }
            return finish(state);
        } catch (RuntimeException e) {
            return fail(state, "GRAPH_NODE_FAILED", message(e));
        }
    }

    private GraphResult approval(ReconcileGraphState state, String reason) {
        state.enter("persist_approval");
        if (state.plan == null) {
            state.plan = compensationPlanner.plan(state.item, state.snapshot, state.diagnosis, state.validation);
        }
        state.approvalRequirement = approvalService.assess(state.plan, state.validation);
        state.approvalSubmission = approvalService.submit(state.batchNo, state.plan, state.approvalRequirement);
        state.outcome = "PENDING_APPROVAL";
        state.failureMessage = reason;
        return finish(state);
    }

    private String lowConfidenceReason(ReconcileGraphState state) {
        if ("EVIDENCE_CONFLICT".equals(state.validation.status())) return "EVIDENCE_CONFLICT";
        if (state.diagnosis.confidence() >= CONFIDENCE_THRESHOLD && state.validation.passed()) return "NONE";
        if (state.snapshot != null && !state.snapshot.missingSources().isEmpty()) return "EVIDENCE_INSUFFICIENT";
        return "EVIDENCE_CONFLICT";
    }

    private GraphResult fail(ReconcileGraphState state, String code, String message) {
        state.outcome = "FAILED";
        state.failureCode = code;
        state.failureMessage = message;
        return finish(state);
    }

    private GraphResult finish(ReconcileGraphState state) {
        state.enter("persist_item_trace");
        traceService.finish(state);
        return new GraphResult(state.outcome, state.graphRunId, state.threadId,
                state.approvalSubmission == null ? null : state.approvalSubmission.approvalNo(),
                state.failureCode, state.failureMessage);
    }

    private String message(Exception e) { return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage(); }

    public record GraphResult(String outcome, String graphRunId, String threadId,
                              String approvalNo, String failureCode, String failureMessage) {
    }
}
