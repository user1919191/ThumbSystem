package com.example.demo.Converter;

import cn.hutool.core.util.IdUtil;
import com.example.demo.MQ.thumb.msg.ThumbEvent;
import com.example.demo.MQ.thumb.msg.ThumbMetricEvent;

public class ThumbConverter {

    private ThumbConverter() {
    }

    public static ThumbMetricEvent thumbEventToMetricEvent(ThumbEvent thumbEvent, String stepStatus,
                                                            String parentStepStatus, String msg) {
        ThumbMetricEvent thumbMetricEvent = new ThumbMetricEvent();
        String id = thumbEvent.getId() == null ? IdUtil.getSnowflake().toString() : thumbEvent.getId();
        thumbMetricEvent.setId(id);
        thumbMetricEvent.setStepStatus(stepStatus);
        thumbMetricEvent.setParentStepStatus(parentStepStatus);
        thumbMetricEvent.setMsg(msg);
        thumbMetricEvent.setThumbEvent(thumbEvent);
        return thumbMetricEvent;
    }
}
