package com.example.demo.MQ.thumb.Consumer;

import cn.hutool.core.lang.Pair;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.example.demo.MQ.thumb.msg.ThumbEvent;
import com.example.demo.mapper.BlogMapper;
import com.example.demo.model.entity.thumb;
import com.example.demo.service.ThumbService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pulsar.client.api.Message;
import org.apache.pulsar.client.api.MessageId;
import org.apache.pulsar.client.api.SubscriptionType;
import org.apache.pulsar.common.schema.SchemaType;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.pulsar.annotation.PulsarListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ThumbConsumer {

    private final BlogMapper blogMapper;

    private final ThumbService thumbService;



    @PulsarListener(
            topics = "thumb-dlq-topic"
    )
    public void consumerMsg(Message<ThumbEvent> messages){
        MessageId messageId = messages.getMessageId();
        log.info("messageId:{} 已经入队",messageId);
    }

    @PulsarListener(
            subscriptionName = "thumb-subscription",
            topics = "thumb-topic",
            subscriptionType = SubscriptionType.Shared ,
            schemaType = SchemaType.JSON ,
            batch = true ,
            // 引用 NACK 重试策略
            negativeAckRedeliveryBackoff = "negativeAckRedeliveryBackoff",
            // 引用 ACK 超时重试策略
            ackTimeoutRedeliveryBackoff = "ackTimeoutRedeliveryBackoff",
            // 引用死信队列策略
            deadLetterPolicy = "deadLetterPolicy"
    )
    @Transactional(rollbackFor =  Exception.class)
    public void processBatch(List<Message<ThumbEvent>> messages){
        ConcurrentHashMap<Long, Long> countConMap = new ConcurrentHashMap<>();
        List<thumb> thumbArrayList = new ArrayList<>();

        LambdaQueryWrapper<thumb> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        AtomicReference<Boolean> removeThumb = new AtomicReference<>(false);

        List<ThumbEvent> thumbEvents = messages.stream().map(Message::getValue).filter(Objects::nonNull).toList();

        Map<Pair<Long, Long>, ThumbEvent> thumbEventMap = thumbEvents.stream().collect(Collectors.groupingBy(e ->
                Pair.of(e.getBlogId(), e.getUserId()) ,Collectors.collectingAndThen(Collectors.toList() ,
                list ->{
                    list.sort(Comparator.comparing(ThumbEvent::getLocalDateTime));
                    if(list.size() % 2 == 0){
                        return null;
                    }
                    return list.get(list.size() - 1);
                }
            )
        ));

        thumbEventMap.forEach((thumbPair,event) -> {
            if(event == null){
                return;
            }
            ThumbEvent.ThumbType finalThumbType = event.getType();
            if(finalThumbType == ThumbEvent.ThumbType.INCREASE){
                countConMap.merge(event.getBlogId(),1L,Long::sum);
                thumb thumb = new thumb();

                thumb.setBlogId(event.getBlogId());
                thumb.setUserId(event.getUserId());
                thumbArrayList.add(thumb);
            }else{
                removeThumb.set(true);
                lambdaQueryWrapper.or().eq(thumb::getUserId, event.getUserId()).eq(thumb::getBlogId, event.getBlogId());
                countConMap.merge(event.getBlogId(),-1L,Long::sum);
                }
            });

            if(removeThumb.get()){
                thumbService.remove(lambdaQueryWrapper);
            }
            batchUpdateBlog(countConMap);
            batchInsertThumbs(thumbArrayList);
        }

    public void batchUpdateBlog(Map<Long, Long> countMap) {
        if (!countMap.isEmpty()) {
            blogMapper.batchUpdateThumbCount(countMap);
        }
    }

    public void batchInsertThumbs(List<thumb> thumbs) {
        if (!thumbs.isEmpty()) {
            thumbService.saveBatch(thumbs, 500);
        }
    }

}
