package com.example.demo.manager.Cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class CacheManager {

    private TopK topK;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    private Cache<String,Object> localCache;

    @Bean
    public TopK getHotKey(){
        topK = new HeavyKeeper(
                100,
                10000,
                5,
                0.92,
                10);
        return topK;
    }

    @Bean
    public Cache<String, Object> localCache() {
        return localCache = Caffeine.newBuilder()
                .maximumSize(1000)
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .build();
    }

    private String buildCacheKey(String hashKey, String key) {
        return hashKey + ":" + key;
    }

    public Object get(String hashKey, String key) {
        // 构造唯一的 composite key
        String compositeKey = buildCacheKey(hashKey, key);

        // 1. 先查本地缓存
        Object value = localCache.getIfPresent(compositeKey);
        if (value != null) {
            log.info("本地缓存获取到数据 {} = {}", compositeKey, value);
            // 记录访问次数（每次访问计数 +1）
            topK.add(key, 1);
            return value;
        }

        // 2. 本地缓存未命中，查询 Redis
        Object redisValue = stringRedisTemplate.opsForHash().get(hashKey, key);
        if (redisValue == null) {
            return null;
        }

        // 3. 记录访问（计数 +1）
        AddResult addResult = topK.add(key, 1);

        // 4. 如果是热 Key 且不在本地缓存，则缓存数据
        if (addResult.isHotKey()) {
            localCache.put(compositeKey, redisValue);
        }
        return redisValue;
    }

    public void putIfPresent(String hashKey, String key, Object value) {
        String compositeKey = buildCacheKey(hashKey, key);
        Object object = localCache.getIfPresent(compositeKey);
        if (object == null) {
            return;
        }
        localCache.put(compositeKey, value);
    }

     //Todo 参考Caffeine降低历史权重的缓存策略

    // 定时清理过期的热 Key 检测数据
    @Scheduled(fixedRate = 20, timeUnit = TimeUnit.SECONDS)
    public void cleanHotKeys() {
        topK.fading();
    }

}
