package com.example.demo.monitor;

/**
 * 业务指标记录接口。
 * 所有方法必须保证线程安全，且埋点异常不得影响主流程。
 */
public interface ThumbMetricsRecorder {

    /** 记录一次 Pulsar 发送成功，costNanos 为发送耗时（纳秒） */
    void recordPulsarSendSuccess(long costNanos);

    /** 记录一次 Pulsar 发送失败 */
    void recordPulsarSendFailure();

    /** 记录一批消费结果 */
    void recordConsumerBatch(int messageCount, long costNanos, boolean success);

    /** 记录消费到的消息条数 */
    void recordConsumerMessage(int count);

    /** 记录消费处理失败条数 */
    void recordConsumerFailure(int count);

    /** 记录一条进入死信队列的消息 */
    void recordConsumerDlq();

    /** 记录当前消费滞后时间（毫秒） */
    void recordConsumerLag(long lagMs);

    /** 记录事件产生到消费完成的一致性延迟（毫秒） */
    void recordConsistencyDelay(long delayMs);

    /** 记录一次对账结果 */
    void recordReconcile(int scannedUsers, int diffCount, long costNanos);

    /** 记录一次补偿发送尝试 */
    void recordCompensationAttempt();

    /** 记录一次补偿发送成功 */
    void recordCompensationSuccess();

    /** 记录一次补偿发送失败 */
    void recordCompensationFailure();
}