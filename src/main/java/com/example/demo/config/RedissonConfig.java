package com.example.demo.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Redisson配置类 - 支持Redis分片集群
 */
@Configuration
public class RedissonConfig {

    @Value("${spring.data.redis.cluster.nodes}")
    private String clusterNodes;

    @Bean
    public RedissonClient redissonClient() {
        Config config = new Config();

        // 配置Redis集群
        String[] nodes = clusterNodes.split(",");
        String[] addresses = new String[nodes.length];
        for (int i = 0; i < nodes.length; i++) {
            addresses[i] = "redis://" + nodes[i].trim();
        }

        config.useClusterServers()
                .addNodeAddress(addresses)
                .setConnectTimeout(5000)
                .setTimeout(5000)
                .setRetryAttempts(3)
                .setRetryInterval(1500)
                .setMasterConnectionPoolSize(64)
                .setSlaveConnectionPoolSize(64)
                .setMasterConnectionMinimumIdleSize(10)
                .setSlaveConnectionMinimumIdleSize(10);

        return Redisson.create(config);
    }
}
