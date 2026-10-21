package com.example.demo.monitor;


import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;


@Component
public class ThumbMetrics implements ThumbMetricsRecorder {

    private final Timer producerSendTimer;
    private final Counter producerSendSuccessCounter;
    private final Counter producerSendFailureCounter;

    private final Counter consumerBatchCounter;
    private final Counter consumerMessageCounter;
    private final Counter consumerFailureCounter;
    private final Counter dlqCounter;
    private final Timer consumerBatchTimer;

    private final Counter compensationAttemptCounter;
    private final Counter compensationSuccessCounter;
    private final Counter compensationFailureCounter;
    private final Timer compensationTimer;

    private final AtomicLong consumerLagMs = new AtomicLong();
    private final AtomicLong consistencyDelayMs = new AtomicLong();
    private final AtomicLong reconcileDiffCount = new AtomicLong();
    private final AtomicLong reconcileScannedUserCount = new AtomicLong();

    public ThumbMetrics(MeterRegistry registry) {
        this.producerSendTimer = Timer.builder("thumb_pulsar_send_seconds")
                .description("Pulsar send latency for thumb events")
                .register(registry);
        this.producerSendSuccessCounter = Counter.builder("thumb_pulsar_send_success_total")
                .description("Total successful Pulsar sends for thumb events")
                .register(registry);
        this.producerSendFailureCounter = Counter.builder("thumb_pulsar_send_failure_total")
                .description("Total failed Pulsar sends for thumb events")
                .register(registry);

        this.consumerBatchCounter = Counter.builder("thumb_consumer_batch_total")
                .description("Total processed thumb consumer batches")
                .register(registry);
        this.consumerMessageCounter = Counter.builder("thumb_consumer_message_total")
                .description("Total thumb messages received by consumer")
                .register(registry);
        this.consumerFailureCounter = Counter.builder("thumb_consumer_failure_total")
                .description("Total thumb consumer failures")
                .register(registry);
        this.dlqCounter = Counter.builder("thumb_consumer_dlq_total")
                .description("Total thumb messages routed to dead letter handling")
                .register(registry);
        this.consumerBatchTimer = Timer.builder("thumb_consumer_batch_seconds")
                .description("Thumb consumer batch processing time")
                .register(registry);

        this.compensationAttemptCounter = Counter.builder("thumb_compensation_attempt_total")
                .description("Total thumb compensation events attempted")
                .register(registry);
        this.compensationSuccessCounter = Counter.builder("thumb_compensation_success_total")
                .description("Total successful thumb compensation sends")
                .register(registry);
        this.compensationFailureCounter = Counter.builder("thumb_compensation_failure_total")
                .description("Total failed thumb compensation sends")
                .register(registry);
        this.compensationTimer = Timer.builder("thumb_compensation_seconds")
                .description("Thumb reconcile and compensation execution time")
                .register(registry);

        Gauge.builder("thumb_consumer_lag_ms", consumerLagMs, AtomicLong::get)
                .description("Current consumer lag in milliseconds based on event time")
                .register(registry);
        Gauge.builder("thumb_consistency_delay_ms", consistencyDelayMs, AtomicLong::get)
                .description("Current end-to-end consistency delay in milliseconds")
                .register(registry);
        Gauge.builder("thumb_reconcile_diff_count", reconcileDiffCount, AtomicLong::get)
                .description("Latest diff count found between Redis and TiDB during reconcile")
                .register(registry);
        Gauge.builder("thumb_reconcile_scanned_user_count", reconcileScannedUserCount, AtomicLong::get)
                .description("Latest scanned user count during reconcile")
                .register(registry);
    }

    @Override
    public void recordPulsarSendSuccess(long costNanos) {
        producerSendSuccessCounter.increment();
        producerSendTimer.record(costNanos, TimeUnit.NANOSECONDS);
    }

    @Override
    public void recordPulsarSendFailure() {
        producerSendFailureCounter.increment();
    }

    @Override
    public void recordConsumerBatch(int messageCount, long costNanos, boolean success) {
        consumerBatchCounter.increment();
        consumerBatchTimer.record(costNanos, TimeUnit.NANOSECONDS);
        if (!success) {
            consumerFailureCounter.increment(messageCount);
        }
    }

    @Override
    public void recordConsumerMessage(int count) {
        consumerMessageCounter.increment(count);
    }

    @Override
    public void recordConsumerFailure(int count) {
        consumerFailureCounter.increment(count);
    }

    @Override
    public void recordConsumerDlq() {
        dlqCounter.increment();
    }

    @Override
    public void recordConsumerLag(long lagMs) {
        consumerLagMs.set(lagMs);
    }

    @Override
    public void recordConsistencyDelay(long delayMs) {
        consistencyDelayMs.set(delayMs);
    }

    @Override
    public void recordReconcile(int scannedUsers, int diffCount, long costNanos) {
        reconcileScannedUserCount.set(scannedUsers);
        reconcileDiffCount.set(diffCount);
        compensationTimer.record(costNanos, TimeUnit.NANOSECONDS);
    }

    @Override
    public void recordCompensationAttempt() {
        compensationAttemptCounter.increment();
    }

    @Override
    public void recordCompensationSuccess() {
        compensationSuccessCounter.increment();
    }

    @Override
    public void recordCompensationFailure() {
        compensationFailureCounter.increment();
    }
}
