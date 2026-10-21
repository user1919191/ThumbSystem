package com.example.demo.model.enums;

import lombok.Getter;

@Getter
public enum StepStatusEnum {
    REDIS_WRITTEN("REDIS_WRITTEN", "Redis写入成功"),
    MQ_SENT("MQ_SENT", "Pulsar生产者发送成功"),
    MQ_SEND_FAILED("MQ_SEND_FAILED", "Pulsar生产者发送失败"),
    MQ_SEND_RETRY_PENDING("MQ_SEND_RETRY_PENDING", "Pulsar发送失败，已写入本地重试表"),
    CONSUMED("CONSUMED", "Pulsar消费者接收成功"),
    CONSUME_FAILED("CONSUME_FAILED", "Pulsar消费者处理失败"),
    DLQ("DLQ", "消息已进入死信队列"),
    DB_COMMITTED("DB_COMMITTED", "DB事务提交成功"),
    DB_COMMIT_FAILED("DB_COMMIT_FAILED", "DB事务提交失败");

    private final String stepId;

    private final String stepName;

    StepStatusEnum(String stepId, String stepName) {
        this.stepId = stepId;
        this.stepName = stepName;
    }
}
