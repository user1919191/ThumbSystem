package com.example.demo.compensation;

/**
 * 对账差异类型：只描述集合差异，不描述“怎么修”
 */
public enum ReconcileDiffType {

    /**
     * Redis 有、DB 无
     */
    REDIS_ONLY,

    /**
     * DB 有、Redis 无
     */
    DB_ONLY
}
