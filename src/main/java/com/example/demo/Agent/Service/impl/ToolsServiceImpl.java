package com.example.demo.Agent.Service.impl;

import com.example.demo.Agent.Service.ToolsService;
import com.example.demo.mapper.BizRetryTaskMapper;
import com.example.demo.model.entity.BizRetryTask;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.apache.pulsar.client.api.Message;
import org.apache.pulsar.client.api.MessageId;
import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.client.api.Reader;
import org.apache.pulsar.client.api.Schema;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class ToolsServiceImpl implements ToolsService {

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 100;
    private static final int MAX_PAYLOAD_CHARS = 4096;
    private final BizRetryTaskMapper retryTaskMapper;
    private final PulsarClient pulsarClient;
    private final MeterRegistry meterRegistry;
    private long previousHttpCount = -1;
    private long previousSampleNanos;

    @Override
    @Tool(description = "Current JVM memory, threads, cumulative GC statistics and HTTP QPS since previous sample")
    public Map<String, Object> queryJvmGcQpsStatus() {
        MemoryUsage heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
        Map<String, Object> jvm = new LinkedHashMap<>();
        jvm.put("heapUsedBytes", heap.getUsed());
        jvm.put("heapCommittedBytes", heap.getCommitted());
        jvm.put("heapMaxBytes", heap.getMax());
        jvm.put("threadCount", ManagementFactory.getThreadMXBean().getThreadCount());
        jvm.put("uptimeMs", ManagementFactory.getRuntimeMXBean().getUptime());
        List<Map<String, Object>> gc = new ArrayList<>();
        for (GarbageCollectorMXBean collector : ManagementFactory.getGarbageCollectorMXBeans()) {
            gc.add(Map.of("name", collector.getName(), "collectionCount", collector.getCollectionCount(),
                    "collectionTimeMs", collector.getCollectionTime()));
        }
        return Map.of("capturedAt", Instant.now().toString(), "jvm", jvm,
                "gc", gc, "httpQps", sampleHttpQps());
    }

    private synchronized Map<String, Object> sampleHttpQps() {
        long requests = meterRegistry.find("http.server.requests").timers().stream()
                .mapToLong(Timer::count).sum();
        long now = System.nanoTime();
        Double qps = previousHttpCount < 0 ? null
                : Math.max(0, requests - previousHttpCount) /
                (Math.max(1L, now - previousSampleNanos) / 1_000_000_000.0);
        previousHttpCount = requests;
        previousSampleNanos = now;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("qps", qps);
        result.put("requestCountSinceStart", requests);
        result.put("sampleReady", qps != null);
        return result;
    }

    @Override
    @Tool(description = "Read thumb-topic or thumb-log-topic without acknowledging messages")
    public Map<String, Object> queryPulsarMessages(
            @ToolParam(description = "thumb-topic or thumb-log-topic") String topic,
            @ToolParam(description = "Maximum messages, capped at 100") Integer limit) {
        String selected = topic == null || topic.isBlank() ? "thumb-topic" : topic.trim();
        if (!"thumb-topic".equals(selected) && !"thumb-log-topic".equals(selected)) {
            throw new IllegalArgumentException("Only thumb-topic and thumb-log-topic are allowed");
        }
        return readTopic(selected, limit);
    }

    @Override
    @Tool(description = "Read thumb-dlq-topic without creating a subscription or acknowledging messages")
    public Map<String, Object> queryDlqMessages(
            @ToolParam(description = "Maximum messages, capped at 100") Integer limit) {
        return readTopic("thumb-dlq-topic", limit);
    }

    private Map<String, Object> readTopic(String topic, Integer limit) {
        List<Map<String, Object>> items = new ArrayList<>();
        try (Reader<byte[]> reader = pulsarClient.newReader(Schema.BYTES)
                .topic(topic).startMessageId(MessageId.earliest).create()) {
            while (items.size() < boundedLimit(limit)) {
                Message<byte[]> message = reader.readNext(300, TimeUnit.MILLISECONDS);
                if (message == null) {
                    break;
                }
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("messageId", message.getMessageId().toString());
                item.put("publishTime", Instant.ofEpochMilli(message.getPublishTime()).toString());
                item.put("properties", message.getProperties());
                String payload = new String(message.getValue(), StandardCharsets.UTF_8);
                item.put("payload", payload.length() > MAX_PAYLOAD_CHARS
                        ? payload.substring(0, MAX_PAYLOAD_CHARS) : payload);
                items.add(item);
            }
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to read Pulsar topic: " + topic, exception);
        }
        return Map.of("topic", topic, "count", items.size(), "items", items);
    }

    @Override
    @Tool(description = "Read-only retry task lookup")
    public Map<String, Object> queryRetryTasks(
            @ToolParam(description = "Event ID; empty skips filter") String eventId,
            @ToolParam(description = "Business key; empty skips filter") String bizKey,
            @ToolParam(description = "Status; empty skips filter") String status,
            @ToolParam(description = "Maximum rows, capped at 100") Integer limit) {
        LambdaQueryWrapper<BizRetryTask> query = new LambdaQueryWrapper<>();
        if (eventId != null && !eventId.isBlank()) {
            query.eq(BizRetryTask::getEventId, eventId.trim());
        }
        if (bizKey != null && !bizKey.isBlank()) {
            query.eq(BizRetryTask::getBizKey, bizKey.trim());
        }
        if (status != null && !status.isBlank()) {
            query.eq(BizRetryTask::getStatus, status.trim());
        }
        query.orderByDesc(BizRetryTask::getCreatedAt).last("LIMIT " + boundedLimit(limit));
        List<BizRetryTask> tasks = retryTaskMapper.selectList(query);
        return Map.of("count", tasks.size(), "items", tasks);
    }

    private int boundedLimit(Integer limit) {
        return limit == null || limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);
    }
}
