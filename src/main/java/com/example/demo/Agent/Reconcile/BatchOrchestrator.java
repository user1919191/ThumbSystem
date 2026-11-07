package com.example.demo.Agent.Reconcile;

import com.example.demo.compensation.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.HashSet;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class BatchOrchestrator {
    private static final int MAX_BATCH_SIZE = 100;
    private final ReconcileGraphRunner graphRunner;

    public ReconcileBatchResult handleBatch(ReconcileDiffBatch batch) {
        long start=System.currentTimeMillis();
        if(batch==null||batch.items()==null||batch.items().isEmpty()) return ReconcileBatchResult.empty(batch==null?null:batch.batchNo());
        if(batch.items().size()>MAX_BATCH_SIZE) return new ReconcileBatchResult(batch.batchNo(),batch.items().size(),0,0,0,batch.items().size(),elapsed(start));
        int success=0, skipped=0, escalated=0, failed=0; Set<String> seen=new HashSet<>();
        for(ReconcileDiffItem item:batch.items()) try {
            validate(item); String key=item.diffKey();
            if(key==null||!seen.add(key)){skipped++;continue;}
            ReconcileGraphRunner.GraphResult result = graphRunner.run(
                    batch.batchNo(), success + skipped + escalated + failed, item);
            switch (result.outcome()) {
                case "SUCCESS" -> success++;
                case "SKIPPED" -> skipped++;
                case "PENDING_APPROVAL", "REJECTED" -> escalated++;
                default -> failed++;
            }
        } catch(Exception ex){failed++;}
        return new ReconcileBatchResult(batch.batchNo(),batch.items().size(),success,skipped,escalated,failed,elapsed(start));
    }
    private void validate(ReconcileDiffItem i){if(i==null||i.userId()==null||i.blogId()==null||i.diffType()==null)throw new IllegalArgumentException("invalid reconcile item");}
    private long elapsed(long s){return System.currentTimeMillis()-s;}
}
