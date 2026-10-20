package com.example.demo.model.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
public enum StepStatusEnum {
    Redis("1","Redis执行成功"),
    MQProducer("2-1","MQ生产者发送成功"),
    MQProducerFail("2-2","MQ生产则会发送失败"),
    MQProducerRetry("2-2-1","MQ生产者投递失败，重试中"),
    MQProducerRetrySuccess("2-2-1-1","MQ生产者投递失败，重试成功"),
    MQProducerRetryFail("2-2-1-2","MQ生产者投递失败，重试失败"),
    MQConsumer("3-1","MQ消费者消费成功"),
    MQConsumerFail("3-2","MQ消费者消费失败"),
    MQConsumerDeadQueue("3-3","MQ消费者消费失败，已经进入死信队列"),
    DBSuccess("4-1","DB写入成功"),
    DBFail("4-2","DB写入失败");




    private final String stepId;

    private final String stepName;

    StepStatusEnum(String stepId, String stepName) {
        this.stepId = stepId;
        this.stepName = stepName;
    }
}
