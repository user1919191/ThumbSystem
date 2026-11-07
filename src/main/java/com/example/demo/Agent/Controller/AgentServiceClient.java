package com.example.demo.Agent.Controller;

import com.example.demo.Agent.Service.AgentService;
import com.example.demo.Agent.Reconcile.BatchOrchestrator;
import com.example.demo.compensation.ReconcileDiffBatch;
import com.example.demo.compensation.ReconcileBatchResult;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class AgentServiceClient {

    @Resource
    private AgentService agentService;

    @jakarta.annotation.Resource
    private BatchOrchestrator batchOrchestrator;

    /**
     * 对账差异批量入口：对账侧只负责产出差异，异常判断与后续处理由 Agent 承接
     */
    public ReconcileBatchResult batchExecute(ReconcileDiffBatch batch) {
        if (batch == null || batch.items() == null || batch.items().isEmpty()) {
            return ReconcileBatchResult.empty(batch == null ? null : batch.batchNo());
        }
        log.info("收到对账差异批次: batchNo={}, batchIndex={}, totalCount={}, size={}",
                batch.batchNo(), batch.batchIndex(), batch.totalCount(), batch.items().size());
        try {
            return batchOrchestrator.handleBatch(batch);
        } catch (RuntimeException ex) {
            log.error("Agent 批次编排失败, batchNo={}", batch.batchNo(), ex);
            return ReconcileBatchResult.failed(batch, ex.getMessage());
        }
    }


}
