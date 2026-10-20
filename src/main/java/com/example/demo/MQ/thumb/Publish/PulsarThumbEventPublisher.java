package com.example.demo.MQ.thumb.Publish;


import cn.hutool.core.util.IdUtil;
import com.example.demo.Converter.ThumbConverter;
import com.example.demo.MQ.thumb.msg.ThumbEvent;
import com.example.demo.MQ.thumb.msg.ThumbMetricEvent;
import com.example.demo.common.ErrorCode;
import com.example.demo.exception.ThrowUtils;
import com.example.demo.model.enums.StepStatusEnum;
import jakarta.annotation.Resource;
import lombok.AllArgsConstructor;
import org.apache.pulsar.client.api.MessageId;
import org.springframework.beans.BeanUtils;
import org.springframework.pulsar.core.PulsarTemplate;
import org.springframework.stereotype.Component;
//"thumb-topic"

@Component
@AllArgsConstructor
public class PulsarThumbEventPublisher implements ThumbEventPublisher{

    @Resource
    private final PulsarTemplate<ThumbEvent> pulsarTemplate;


    @Override
    public Boolean Publication(ThumbEvent thumbEvent) {
        ThrowUtils.throwIf(thumbEvent == null, ErrorCode.NOT_FOUND_ERROR);
        String id = IdUtil.getSnowflake().toString();
        ThumbMetricEvent thumbProducerMetricEvent = new ThumbMetricEvent();
        MessageId send = null;
       try{
           send = pulsarTemplate.send("thumb-topic",thumbEvent);
       }catch (Exception e){
           thumbProducerMetricEvent.setId(id);
           thumbProducerMetricEvent.setThumbEvent(thumbEvent);
           thumbProducerMetricEvent.setStepStatus(StepStatusEnum.MQProducerFail.getStepId());
           thumbProducerMetricEvent.setParentStepStatus(StepStatusEnum.Redis.getStepId());
           thumbProducerMetricEvent.setMsg("进入重试");
           pulsarTemplate.send("thumb-log-topic",thumbEvent);
           return false;
       }
       thumbProducerMetricEvent.setId(id);
       thumbProducerMetricEvent.setThumbEvent(thumbEvent);
       thumbProducerMetricEvent.setStepStatus(StepStatusEnum.MQProducer.getStepId());
       thumbProducerMetricEvent.setParentStepStatus(StepStatusEnum.Redis.getStepId());
       thumbProducerMetricEvent.setMsg("PulsarID："+send);
       pulsarTemplate.send("thumb-topic",thumbEvent);
       return true;
    }

    @Override
    public Boolean syncPublish(ThumbEvent thumbEvent) {
        return null;
    }
}
