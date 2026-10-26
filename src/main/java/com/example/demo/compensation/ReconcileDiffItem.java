package com.example.demo.compensation;

import java.time.LocalDateTime;

/**
 * 单条差异：对账的唯一产出物
 *
 * @param diffKey      userId:blogId:diffType，幂等键
 * @param diffType     差异类型
 * @param userId       用户 ID
 * @param blogId       博客 ID
 * @param redisValue   Redis 侧的值，REDIS_ONLY 时为 "1"
 * @param dbThumbId    DB 明细主键，DB_ONLY 时有值
 * @param dbCreateTime DB 明细创建时间，DB_ONLY 时有值
 * @param detectedAt   发现时间
 */
public record ReconcileDiffItem(
        String diffKey,
        ReconcileDiffType diffType,
        Long userId,
        Long blogId,
        String redisValue,
        Long dbThumbId,
        LocalDateTime dbCreateTime,
        LocalDateTime detectedAt) {
}
