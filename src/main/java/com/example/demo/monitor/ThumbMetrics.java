package com.example.demo.monitor;

public class ThumbMetrics implements ThumbMetricsRecorder {
    @Override
    public void recordPulsarSendSuccess(long costNanos) {

    }

    @Override
    public void recordPulsarSendFailure() {

    }

    @Override
    public void recordConsumerBatch(int messageCount, long costNanos, boolean success) {

    }

    @Override
    public void recordConsumerMessage(int count) {

    }

    @Override
    public void recordConsumerFailure(int count) {

    }

    @Override
    public void recordConsumerDlq() {

    }

    @Override
    public void recordConsumerLag(long lagMs) {

    }

    @Override
    public void recordConsistencyDelay(long delayMs) {

    }

    @Override
    public void recordReconcile(int scannedUsers, int diffCount, long costNanos) {

    }

    @Override
    public void recordCompensationAttempt() {

    }

    @Override
    public void recordCompensationSuccess() {

    }

    @Override
    public void recordCompensationFailure() {

    }
}
