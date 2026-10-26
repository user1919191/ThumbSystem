package com.example.demo.Agent.Controller;

import com.example.demo.Agent.Service.AgentService;
import com.example.demo.compensation.ReconcileDiffBatch;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class AgentServiceClient {

    @Resource
    private AgentService agentService;

    public void execute(){

    }

    /**
     * 对账差异批量入口：对账侧只负责产出差异，异常判断与后续处理由 Agent 承接
     */
    public void batchExecute(ReconcileDiffBatch batch) {
        log.info("收到对账差异批次: batchNo={}, batchIndex={}, totalCount={}, size={}",
                batch.batchNo(), batch.batchIndex(), batch.totalCount(), batch.items().size());
    }


}
