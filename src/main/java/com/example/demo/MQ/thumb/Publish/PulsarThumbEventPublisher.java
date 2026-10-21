package com.example.demo.MQ.thumb.Publish;

import cn.hutool.core.util.IdUtil;
import com.example.demo.MQ.thumb.msg.ThumbEvent;
import com.example.demo.common.ErrorCode;
import com.example.demo.exception.ThrowUtils;
import com.example.demo.model.enums.StepStatusEnum;
import com.example.demo.monitor.ThumbMetricsRecorder;
import com.example.demo.service.PulsarRetryTaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pulsar.client.api.MessageId;
import org.springframework.pulsar.core.PulsarTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class PulsarThumbEventPublisher implements ThumbEventPublisher {

    private static final String THUMB_TOPIC = "thumb-topic";

    private final PulsarTemplate<ThumbEvent> pulsarTemplate;
    private final ThumbMetricsRecorder thumbMetricsRecorder;
    private final ThumbLogPublisher thumbLogPublisher;
    private final PulsarRetryTaskService retryTaskService;

    @Override
    public Boolean Publication(ThumbEvent thumbEvent) {
        prepareEvent(thumbEvent);
        thumbLogPublisher.publish(thumbEvent, StepStatusEnum.REDIS_WRITTEN, null,
                "Redis状态已更新，准备发送Pulsar消息");

        long startNanos = System.nanoTime();
        try {
            MessageId messageId = pulsarTemplate.send(THUMB_TOPIC, thumbEvent);
            thumbMetricsRecorder.recordPulsarSendSuccess(System.nanoTime() - startNanos);
            thumbLogPublisher.publish(thumbEvent, StepStatusEnum.MQ_SENT,
                    StepStatusEnum.REDIS_WRITTEN, "Pulsar messageId=" + messageId);
            return true;
        } catch (Exception exception) {
            handleSendFailure(thumbEvent, exception);
            return false;
        }
    }

    @Override
    public Boolean syncPublish(ThumbEvent thumbEvent) {
        prepareEvent(thumbEvent);
        thumbLogPublisher.publish(thumbEvent, StepStatusEnum.REDIS_WRITTEN, null,
                "Redis状态已更新，准备异步发送Pulsar消息");

        long startNanos = System.nanoTime();
        try {
            pulsarTemplate.sendAsync(THUMB_TOPIC, thumbEvent)
                    .whenComplete((sendResult, throwable) -> {
                        if (throwable == null) {
                            thumbMetricsRecorder.recordPulsarSendSuccess(
                                    System.nanoTime() - startNanos);
                            thumbLogPublisher.publish(thumbEvent, StepStatusEnum.MQ_SENT,
                                    StepStatusEnum.REDIS_WRITTEN,
                                    "Pulsar异步发送成功: " + sendResult);
                            return;
                        }
                        try {
                            handleSendFailure(thumbEvent, throwable);
                        } catch (RuntimeException persistException) {
                            log.error("Pulsar异步发送失败且本地重试任务落表失败: eventId={}",
                                    thumbEvent.getId(), persistException);
                        }
                    });
            return true;
        } catch (Exception exception) {
            handleSendFailure(thumbEvent, exception);
            return false;
        }
    }

    private void prepareEvent(ThumbEvent event) {
        ThrowUtils.throwIf(event == null, ErrorCode.NOT_FOUND_ERROR);
        if (event.getId() == null || event.getId().isBlank()) {
            event.setId(IdUtil.getSnowflakeNextIdStr());
        }
    }

    private void handleSendFailure(ThumbEvent event, Throwable throwable) {
        thumbMetricsRecorder.recordPulsarSendFailure();
        thumbLogPublisher.publish(event, StepStatusEnum.MQ_SEND_FAILED,
                StepStatusEnum.REDIS_WRITTEN, throwable.getMessage());
        retryTaskService.saveMqSendFailure(event, throwable);
        thumbLogPublisher.publish(event, StepStatusEnum.MQ_SEND_RETRY_PENDING,
                StepStatusEnum.MQ_SEND_FAILED, "已写入biz_retry_task等待重试");
    }
}
