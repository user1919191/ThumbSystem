package com.example.demo.config;

import org.apache.pulsar.client.api.DeadLetterPolicy;
import org.apache.pulsar.client.api.RedeliveryBackoff;
import org.apache.pulsar.client.impl.MultiplierRedeliveryBackoff;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


@Configuration
public class PlusarConfig {

    // 定义 NACK 重试策略
    @Bean
    public RedeliveryBackoff negativeAckRedeliveryBackoff() {
        // 示例：指数退避策略（最小100ms，最大60s）
        return MultiplierRedeliveryBackoff.builder()
                .minDelayMs(100)
                .maxDelayMs(60 * 1000)
                .multiplier(2)
                .build();
    }

    // 定义 ACK 超时重试策略
    @Bean
    public RedeliveryBackoff ackTimeoutRedeliveryBackoff() {
        return MultiplierRedeliveryBackoff.builder()
                .minDelayMs(500)
                .maxDelayMs(10 * 60 * 1000)
                .multiplier(3)
                .build();
    }

    // 定义死信队列策略
    @Bean
    public DeadLetterPolicy deadLetterPolicy() {
        return DeadLetterPolicy.builder()
                .maxRedeliverCount(3)  // 最大重试次数
                .deadLetterTopic("thumb-dlq-topic")  // 死信队列topic
                .build();
    }
}
