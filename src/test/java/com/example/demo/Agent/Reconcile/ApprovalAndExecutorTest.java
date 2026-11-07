package com.example.demo.Agent.Reconcile;

import com.example.demo.Agent.Service.ToolsService;
import com.example.demo.mapper.CompensationApprovalMapper;
import com.example.demo.model.entity.CompensationApproval;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ApprovalAndExecutorTest {

    @Test
    void approvalPolicySendsHumanAndHighRiskPlansToDatabase() {
        CompensationApprovalMapper mapper = mock(CompensationApprovalMapper.class);
        CompensationApprovalService service = new CompensationApprovalService(mapper, new ObjectMapper());

        CompensationApprovalService.ApprovalRequirement human = service.assess(
                plan(true, "MEDIUM", "A5"), passed());
        CompensationApprovalService.ApprovalRequirement highRisk = service.assess(
                plan(false, "HIGH", "A7"), passed());

        assertTrue(human.required());
        assertEquals(1, human.requiredApprovals());
        assertTrue(highRisk.required());
        assertEquals(2, highRisk.requiredApprovals());
    }

    @Test
    void approvalSubmissionPersistsNormalizedRequest() {
        CompensationApprovalMapper mapper = mock(CompensationApprovalMapper.class);
        when(mapper.selectOne(any())).thenReturn(null);
        CompensationApprovalService service = new CompensationApprovalService(mapper, new ObjectMapper());
        CompensationPlan plan = plan(true, "MEDIUM", "A5");

        CompensationApprovalService.ApprovalSubmission result = service.submit("batch-1", plan,
                new CompensationApprovalService.ApprovalRequirement(true, "HUMAN_REQUIRED", 1));

        assertEquals("PENDING", result.status());
        verify(mapper).insert(argThat((CompensationApproval row) -> "batch-1".equals(row.getBatchNo())
                && row.getUserId() == 2L && row.getBlogId() == 1L
                && "PENDING".equals(row.getApprovalStatus())));
    }

    @Test
    void autoExecutablePlanDoesNotRequireApproval() {
        CompensationApprovalService service = new CompensationApprovalService(
                mock(CompensationApprovalMapper.class), new ObjectMapper());

        CompensationApprovalService.ApprovalRequirement requirement = service.assess(
                plan(false, "MEDIUM", "A5"), passed());

        assertFalse(requirement.required());
    }

    @Test
    void executorDeduplicatesCompletedPlan() {
        ToolsService tools = mock(ToolsService.class);
        when(tools.insertDbThumb(2L, 1L, false)).thenReturn(Map.of(
                "status", "EXECUTED", "message", "ok"));
        CompensationExecutor executor = new CompensationExecutor(tools);

        ExecutionResult first = executor.execute(plan(false, "MEDIUM", "A5"));
        ExecutionResult second = executor.execute(plan(false, "MEDIUM", "A5"));

        assertEquals("EXECUTED", first.status());
        assertEquals("SKIPPED", second.status());
        verify(tools, times(1)).insertDbThumb(2L, 1L, false);
    }

    private CompensationPlan plan(boolean needHuman, String risk, String classification) {
        return new CompensationPlan("plan-1", "ev-1", "2:1:REDIS_ONLY", classification,
                0.95, needHuman, "WRITE_DB", Map.of("userId", 2L, "blogId", 1L),
                true, risk, "idem-1", List.of("thumb detail exists"),
                new CompensationPlan.DryRunResult("PLANNED", "ok", Map.of()),
                "LLM", "READY");
    }

    private DiagnosisValidator.ValidationResult passed() {
        return new DiagnosisValidator.ValidationResult(true, "PASSED", "ok",
                "A5", 0.95, false);
    }
}
