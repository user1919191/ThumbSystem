package com.example.demo.config;

import com.example.demo.model.entity.blog;
import com.example.demo.model.entity.thumb;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class CacheConfig {

    @Bean
    public Cache<String, blog> blogCache() {
        return Caffeine.newBuilder().initialCapacity(200)
                .maximumSize(1000).expireAfterWrite(10, TimeUnit.MINUTES)
                .build();
    }

    @Bean
    public Cache<String, thumb> thumbCache() {
        return Caffeine.newBuilder().initialCapacity(200)
                .maximumSize(1000)
                .expireAfterWrite(10, TimeUnit.MINUTES).build();
    }


}
