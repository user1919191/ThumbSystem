package com.example.demo.compensation;

public record ReconcileBatchResult(String batchNo, int total, int success, int skipped,
                                   int escalated, int failed, long costMs) {
    public static ReconcileBatchResult empty(String batchNo) { return new ReconcileBatchResult(batchNo,0,0,0,0,0,0); }
    public static ReconcileBatchResult failed(ReconcileDiffBatch b, String ignored) { int n=b==null||b.items()==null?0:b.items().size(); return new ReconcileBatchResult(b==null?null:b.batchNo(),n,0,0,0,n,0); }
}
