package com.example.demo.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ea_reconcile_item_trace")
public class ReconcileItemTrace {
    @TableId(type = IdType.AUTO)
    private Long id;
    @TableField("trace_no") private String traceNo;
    @TableField("graph_run_id") private String graphRunId;
    @TableField("thread_id") private String threadId;
    @TableField("batch_no") private String batchNo;
    @TableField("sequence_no") private Integer sequenceNo;
    @TableField("diff_key") private String diffKey;
    @TableField("diff_type") private String diffType;
    @TableField("user_id") private Long userId;
    @TableField("blog_id") private Long blogId;
    @TableField("current_node") private String currentNode;
    private String outcome;
    @TableField("failure_code") private String failureCode;
    @TableField("failure_message") private String failureMessage;
    @TableField("evidence_id") private String evidenceId;
    @TableField("evidence_hash") private String evidenceHash;
    private String classification;
    private Double confidence;
    @TableField("validation_status") private String validationStatus;
    @TableField("plan_id") private String planId;
    private String action;
    @TableField("approval_no") private String approvalNo;
    @TableField("execution_status") private String executionStatus;
    @TableField("idempotency_key") private String idempotencyKey;
    @TableField("consistency_status") private String consistencyStatus;
    @TableField("state_json") private String stateJson;
    @TableField("started_at") private LocalDateTime startedAt;
    @TableField("finished_at") private LocalDateTime finishedAt;
    @TableField("created_at") private LocalDateTime createdAt;
}
