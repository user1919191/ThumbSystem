package com.example.demo.MQ.thumb.Publish;

import com.example.demo.Converter.ThumbConverter;
import com.example.demo.MQ.thumb.msg.ThumbEvent;
import com.example.demo.MQ.thumb.msg.ThumbMetricEvent;
import com.example.demo.model.enums.StepStatusEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.pulsar.core.PulsarTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ThumbLogPublisher {

    public static final String THUMB_LOG_TOPIC = "thumb-log-topic";

    private final PulsarTemplate<ThumbMetricEvent> pulsarTemplate;

    public void publish(ThumbEvent event, StepStatusEnum status,
                        StepStatusEnum parentStatus, String message) {
        ThumbMetricEvent metricEvent = ThumbConverter.thumbEventToMetricEvent(
                event,
                status.getStepId(),
                parentStatus == null ? null : parentStatus.getStepId(),
                message);
        try {
            pulsarTemplate.send(THUMB_LOG_TOPIC, metricEvent);
        } catch (Exception exception) {
            log.error("发送点赞链路日志失败: eventId={}, status={}",
                    event.getId(), status.getStepId(), exception);
        }
    }
}
