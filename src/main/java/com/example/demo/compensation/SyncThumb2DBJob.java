package com.example.demo.compensation;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.date.DateTime;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.text.StrPool;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.demo.MQ.thumb.msg.ThumbEvent;
import com.example.demo.mapper.BlogMapper;
import com.example.demo.model.entity.thumb;
import com.example.demo.model.enums.ThumbTypeEnum;
import com.example.demo.service.ThumbService;
import com.example.demo.utils.RedisKeyUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.catalina.User;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
public class SyncThumb2DBJob {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private ThumbService thumbService;

    @Resource
    private BlogMapper blogMapper;

    @Scheduled(initialDelay = 10000, fixedDelay = 10000)
    public void syncThumb2DB() {
        DateTime date = DateUtil.date();
        String newDate = DateUtil.format(date, "yyyy-MM-dd HH:mm:") + (DateUtil.second(date) / 10 -1) * 10;
        syncThumb2DBData(newDate);

    }

    public void syncThumb2DBData(String newDate){

        String tempThumbKey = RedisKeyUtil.getTempThumbKey(newDate);
        Map<Object, Object> allTempThumbMap = stringRedisTemplate.opsForHash().entries(tempThumbKey);
        boolean thumbMapEmpty = CollUtil.isEmpty(allTempThumbMap);

        HashMap<Long,Long> thumbCountMap = new HashMap<>();
        if(thumbMapEmpty){
           return;
        }
        ArrayList<thumb> thumbList = new ArrayList<>();
        LambdaQueryWrapper<thumb> thumbLambdaQueryWrapper = new LambdaQueryWrapper<>();
        AtomicBoolean atomicRemove = new AtomicBoolean(false);

        for(Object userThumbObj : allTempThumbMap.keySet()) {
            String userThumb = (String) userThumbObj;
            String[] userThumbKey = userThumb.split(StrPool.COLON);
            Long userId = Long.valueOf(userThumbKey[0]);
            Long blogId = Long.valueOf(userThumbKey[1]);
            Integer thumbType = Integer.valueOf(allTempThumbMap.get(userThumbObj).toString());

            if (thumbType == ThumbTypeEnum.INCR.getValue()) {
                thumb thumb = new thumb();
                thumb.setUserId(userId);
                thumb.setBlogId(blogId);
                thumbList.add(thumb);
            } else {
                if (thumbType == ThumbTypeEnum.DECR.getValue()) {
                    atomicRemove.set(true);
                    thumbLambdaQueryWrapper.or().eq(thumb::getUserId, userId).eq(thumb::getBlogId, blogId);
                } else {
                    if (thumbType == ThumbTypeEnum.NON.getValue()) {
                        log.warn("数据异常：{}", userId + "," + blogId + "," + thumbType);
                    }
                    continue;
                }
            }
            thumbCountMap.put(blogId,thumbCountMap.getOrDefault(blogId,0L) +thumbType);
        }

        thumbService.saveBatch(thumbList);

        if(atomicRemove.get()){
            thumbService.remove(thumbLambdaQueryWrapper);
        }

        if(!thumbCountMap.isEmpty()){
            blogMapper.batchUpdateThumbCount(thumbCountMap);
        }

        Thread.startVirtualThread(() ->{
            stringRedisTemplate.delete(tempThumbKey);
        });
    }
}
