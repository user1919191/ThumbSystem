package com.example.demo.constant;

public interface ThumbConstant {

    /**
     * 用户点赞 hash key
     */
    String USER_THUMB_KEY_PREFIX = "thumb:";

    Long UN_THUMB_CONSTANT = 0L;

    /**
     * 临时 点赞记录 key
     */
    String TEMP_THUMB_KEY_PREFIX = "thumb:temp:%s";

    /**
     * 分布式锁 key 前缀
     */
    String THUMB_LOCK_KEY_PREFIX = "lock:thumb:user:";

    /**
     * 缓存过期时间（7天）
     */
    Long CACHE_EXPIRE_DAYS = 7L;

    /**
     * Pulsar消息幂等性记录前缀
     */
    String PULSAR_MESSAGE_ID_PREFIX = "pulsar:msg:";

    /**
     * Pulsar消息幂等性记录过期时间（7天）
     */
    Long PULSAR_MESSAGE_EXPIRE_DAYS = 7L;

    /**
     * 补偿任务锁前缀
     */
    String COMPENSATION_LOCK_PREFIX = "lock:compensation:";

    /**
     * Pulsar发送失败记录前缀（用于补偿）
     */
    String PULSAR_FAILED_PREFIX = "pulsar:failed:";
}
