package com.example.demo.compensation;

import java.util.List;

/**
 * 一批差异：对账任务 -> Agent 的传输单元
 *
 * @param batchNo    一次对账任务一个 batchNo
 * @param trigger    触发方式：SCHEDULED / MANUAL / DLQ_ALERT
 * @param batchIndex 批次序号，从 0 开始
 * @param totalCount 本次对账累计差异数
 * @param items      本批差异
 */
public record ReconcileDiffBatch(
        String batchNo,
        String trigger,
        int batchIndex,
        int totalCount,
        List<ReconcileDiffItem> items) {
}
