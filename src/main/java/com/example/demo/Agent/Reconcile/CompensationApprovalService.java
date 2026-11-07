package com.example.demo.Agent.Reconcile;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.demo.mapper.CompensationApprovalMapper;
import com.example.demo.model.entity.CompensationApproval;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CompensationApprovalService {
    private static final double MIN_AUTO_CONFIDENCE = 0.8;
    private final CompensationApprovalMapper mapper;
    private final ObjectMapper objectMapper;

    public ApprovalRequirement assess(CompensationPlan plan,
                                      DiagnosisValidator.ValidationResult validation) {
        if (validation == null || !validation.passed())
            return new ApprovalRequirement(true, "VALIDATION_" + (validation == null ? "MISSING" : validation.status()), 1);
        if (plan == null) return new ApprovalRequirement(true, "PLAN_MISSING", 1);
        if ("SKIP".equals(plan.action()) || "SKIP".equals(plan.status()))
            return new ApprovalRequirement(false, "NO_ACTION", 0);
        if ("ESCALATE".equals(plan.action())) return new ApprovalRequirement(true, "ESCALATED", 1);
        if ("INVALID".equals(plan.status())) return new ApprovalRequirement(true, "PLAN_INVALID", 1);
        if (plan.needHuman()) return new ApprovalRequirement(true, "HUMAN_REQUIRED", 1);
        if (plan.confidence() < MIN_AUTO_CONFIDENCE) return new ApprovalRequirement(true, "LOW_CONFIDENCE", 1);
        if ("RULE_FALLBACK".equals(plan.source())) return new ApprovalRequirement(true, "RULE_FALLBACK", 1);
        if ("HIGH".equals(plan.risk()) || "A7".equals(plan.classification()))
            return new ApprovalRequirement(true, "HIGH_RISK", 2);
        if ("A6".equals(plan.classification()) || "A8".equals(plan.classification()))
            return new ApprovalRequirement(true, "MANUAL_CLASSIFICATION", 1);
        return new ApprovalRequirement(false, "AUTO_EXECUTABLE", 0);
    }

    public ApprovalSubmission submit(String batchNo, CompensationPlan plan,
                                     ApprovalRequirement requirement) {
        if (plan == null || requirement == null || !requirement.required())
            throw new IllegalArgumentException("An approval-required plan is required");
        CompensationApproval existing = findByIdempotencyKey(plan.idempotencyKey());
        if (existing != null) return new ApprovalSubmission(existing.getApprovalNo(), "EXISTING", requirement.reason());

        CompensationApproval row = toRow(batchNo, plan, requirement);
        try {
            mapper.insert(row);
            return new ApprovalSubmission(row.getApprovalNo(), "PENDING", requirement.reason());
        } catch (DuplicateKeyException duplicate) {
            existing = findByIdempotencyKey(plan.idempotencyKey());
            if (existing == null) throw duplicate;
            return new ApprovalSubmission(existing.getApprovalNo(), "EXISTING", requirement.reason());
        }
    }

    private CompensationApproval findByIdempotencyKey(String key) {
        return mapper.selectOne(new LambdaQueryWrapper<CompensationApproval>()
                .eq(CompensationApproval::getIdempotencyKey, key).last("LIMIT 1"));
    }

    private CompensationApproval toRow(String batchNo, CompensationPlan plan,
                                       ApprovalRequirement requirement) {
        CompensationApproval row = new CompensationApproval();
        row.setApprovalNo("apr-" + UUID.randomUUID());
        row.setBatchNo(batchNo);
        row.setPlanId(plan.planId());
        row.setEvidenceId(plan.evidenceId());
        row.setDiffKey(plan.diffKey());
        row.setClassification(plan.classification());
        row.setAction(plan.action());
        row.setUserId(longParam(plan, "userId"));
        row.setBlogId(longParam(plan, "blogId"));
        row.setRisk(plan.risk());
        row.setConfidence(BigDecimal.valueOf(plan.confidence()));
        row.setSource(plan.source());
        row.setReason(requirement.reason());
        row.setPlanStatus(plan.status());
        row.setApprovalStatus("PENDING");
        row.setRequiredApprovals(requirement.requiredApprovals());
        row.setApprovedCount(0);
        row.setIdempotencyKey(plan.idempotencyKey());
        row.setDryRunStatus(plan.dryRunResult() == null ? null : plan.dryRunResult().status());
        row.setDryRunMessage(plan.dryRunResult() == null ? null : plan.dryRunResult().message());
        row.setParamsJson(json(plan.params()));
        row.setPreviewJson(json(plan.dryRunResult() == null ? null : plan.dryRunResult().preview()));
        LocalDateTime now = LocalDateTime.now();
        row.setCreatedAt(now);
        row.setUpdatedAt(now);
        return row;
    }

    private Long longParam(CompensationPlan plan, String key) {
        Object value = plan.params().get(key);
        return value instanceof Number number ? number.longValue()
                : value == null ? null : Long.valueOf(String.valueOf(value));
    }
    private String json(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (JsonProcessingException e) { throw new IllegalStateException("Cannot serialize approval data", e); }
    }

    public record ApprovalRequirement(boolean required, String reason, int requiredApprovals) {
    }
    public record ApprovalSubmission(String approvalNo, String status, String reason) {
    }
}
