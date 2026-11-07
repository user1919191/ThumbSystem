package com.example.demo.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ea_reconcile_agent_round")
public class ReconcileAgentRound {
    @TableId(type = IdType.AUTO) private Long id;
    @TableField("graph_run_id") private String graphRunId;
    @TableField("thread_id") private String threadId;
    @TableField("round_no") private Integer roundNo;
    @TableField("evidence_id") private String evidenceId;
    @TableField("evidence_hash") private String evidenceHash;
    private String classification;
    private Double confidence;
    @TableField("low_confidence_reason") private String lowConfidenceReason;
    @TableField("validation_status") private String validationStatus;
    @TableField("validation_reason") private String validationReason;
    @TableField("prompt_version") private String promptVersion;
    @TableField("parse_status") private String parseStatus;
    @TableField("raw_output") private String rawOutput;
    @TableField("trace_json") private String traceJson;
    @TableField("created_at") private LocalDateTime createdAt;
}
