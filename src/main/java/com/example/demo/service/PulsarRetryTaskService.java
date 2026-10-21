package com.example.demo.service;

import cn.hutool.core.util.IdUtil;
import com.example.demo.MQ.thumb.msg.ThumbEvent;
import com.example.demo.mapper.BizRetryTaskMapper;
import com.example.demo.model.entity.BizRetryTask;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class PulsarRetryTaskService {

    private static final String BIZ_TYPE = "thumb";
    private static final String SCENE = "MQ_SEND";
    private static final String PENDING = "PENDING";
    private static final int MAX_ATTEMPT = 5;
    private static final int MAX_ERROR_LENGTH = 512;

    private final BizRetryTaskMapper retryTaskMapper;
    private final ObjectMapper objectMapper;

    public void saveMqSendFailure(ThumbEvent event, Throwable throwable) {
        BizRetryTask task = new BizRetryTask();
        task.setTaskNo(IdUtil.fastSimpleUUID());
        task.setBizType(BIZ_TYPE);
        task.setBizKey(event.getBlogId() + ":" + event.getUserId());
        task.setEventId(event.getId());
        task.setScene(SCENE);
        task.setPayload(toJson(event));
        task.setStatus(PENDING);
        task.setAttempt(0);
        task.setMaxAttempt(MAX_ATTEMPT);
        task.setNextRetryAt(LocalDateTime.now());
        task.setLastError(truncate(throwable.getMessage()));
        task.setIdempotentKey(SCENE + ":" + BIZ_TYPE + ":" + event.getId());
        task.setVersion(0);
        try {
            retryTaskMapper.insert(task);
        } catch (DuplicateKeyException duplicate) {
            log.info("Pulsar重试任务已存在: eventId={}", event.getId());
        }
    }

    private String toJson(ThumbEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("序列化Pulsar重试任务失败", exception);
        }
    }

    private String truncate(String message) {
        if (message == null || message.length() <= MAX_ERROR_LENGTH) {
            return message;
        }
        return message.substring(0, MAX_ERROR_LENGTH);
    }
}
