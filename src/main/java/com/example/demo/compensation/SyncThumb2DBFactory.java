package com.example.demo.compensation;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjUtil;
import com.example.demo.constant.ThumbConstant;
import com.example.demo.utils.RedisKeyUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

@Slf4j
@Component
public class SyncThumb2DBFactory {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private SyncThumb2DBJob syncThumb2DBJob;

    @Scheduled(cron = "0 0 2 * * ?")
    public void syncThumb2DB() {
        log.info("开始补偿数据");
        Set<String> thumbKeys = stringRedisTemplate.keys(RedisKeyUtil.getTempThumbKey("") + "*");
        Set<String> needHandleDataSet = new HashSet<>();
        thumbKeys.stream().filter(ObjUtil::isNotNull).forEach(thumbKey -> needHandleDataSet.add(thumbKey.replace(ThumbConstant.TEMP_THUMB_KEY_PREFIX.formatted(""), "")));

        if (CollUtil.isEmpty(needHandleDataSet)) {
            log.info("没有需要补偿的临时数据");
            return;
        }
        // 补偿数据
        for (String date : needHandleDataSet) {
            syncThumb2DBJob.syncThumb2DBData(date);
        }
        log.info("临时数据补偿完成");
    }
}
