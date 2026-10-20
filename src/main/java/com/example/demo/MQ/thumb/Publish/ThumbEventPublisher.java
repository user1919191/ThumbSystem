package com.example.demo.MQ.thumb.Publish;


import com.example.demo.MQ.thumb.msg.ThumbEvent;

public interface ThumbEventPublisher {

    Boolean Publication(ThumbEvent thumbEvent);

    Boolean syncPublish(ThumbEvent thumbEvent);
}
