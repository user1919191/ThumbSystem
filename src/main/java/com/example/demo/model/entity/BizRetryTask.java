package com.example.demo.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("biz_retry_task")
public class BizRetryTask {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("task_no")
    private String taskNo;

    @TableField("biz_type")
    private String bizType;

    @TableField("biz_key")
    private String bizKey;

    @TableField("event_id")
    private String eventId;

    private String scene;

    private String payload;

    private String status;

    private Integer attempt;

    @TableField("max_attempt")
    private Integer maxAttempt;

    @TableField("next_retry_at")
    private LocalDateTime nextRetryAt;

    @TableField("last_error")
    private String lastError;

    @TableField("idempotent_key")
    private String idempotentKey;

    private String owner;

    private Integer version;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
