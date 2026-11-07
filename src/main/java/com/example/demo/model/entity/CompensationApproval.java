package com.example.demo.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("ea_compensation_approval")
public class CompensationApproval {
    @TableId(type = IdType.AUTO)
    private Long id;
    @TableField("approval_no")
    private String approvalNo;
    @TableField("batch_no")
    private String batchNo;
    @TableField("plan_id")
    private String planId;
    @TableField("evidence_id")
    private String evidenceId;
    @TableField("diff_key")
    private String diffKey;
    private String classification;
    private String action;
    @TableField("user_id")
    private Long userId;
    @TableField("blog_id")
    private Long blogId;
    private String risk;
    private BigDecimal confidence;
    private String source;
    private String reason;
    @TableField("plan_status")
    private String planStatus;
    @TableField("approval_status")
    private String approvalStatus;
    @TableField("required_approvals")
    private Integer requiredApprovals;
    @TableField("approved_count")
    private Integer approvedCount;
    @TableField("idempotency_key")
    private String idempotencyKey;
    @TableField("dry_run_status")
    private String dryRunStatus;
    @TableField("dry_run_message")
    private String dryRunMessage;
    @TableField("params_json")
    private String paramsJson;
    @TableField("preview_json")
    private String previewJson;
    @TableField("created_at")
    private LocalDateTime createdAt;
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
