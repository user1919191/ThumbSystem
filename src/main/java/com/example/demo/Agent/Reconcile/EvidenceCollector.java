package com.example.demo.Agent.Reconcile;

import com.example.demo.Agent.Logs.LogSearchResult;
import com.example.demo.Agent.Logs.LogSearchService;
import com.example.demo.Agent.Service.ToolsService;
import com.example.demo.compensation.ReconcileDiffItem;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

/** Collects independent read-only sources and never turns an unavailable source into false. */
@Service
@RequiredArgsConstructor
public class EvidenceCollector {
    private final ToolsService tools;
    private final LogSearchService logs;

    public EvidenceSnapshot collect(ReconcileDiffItem item, String batchNo, int sequence) {
        Objects.requireNonNull(item, "item");
        long started = System.nanoTime();
        String diffKey = item.diffKey() == null || item.diffKey().isBlank()
                ? item.userId() + ":" + item.blogId() + ":" + item.diffType() : item.diffKey();
        EvidenceSnapshot.RedisEvidence redis = redis(item);
        EvidenceSnapshot.DbEvidence db = db(item);
        EvidenceSnapshot.DlqEvidence dlq = dlq(item);
        String bizKey = item.userId() + ":" + item.blogId();
        EvidenceSnapshot.RetryTaskEvidence retry = retry(item, bizKey);
        EvidenceSnapshot.LogEvidence log = logEvidence(item, bizKey);
        List<String> missing = new ArrayList<>();
        if (!redis.available()) missing.add("redis");
        if (!db.available()) missing.add("db");
        if (!dlq.available()) missing.add("dlq");
        if (!retry.available()) missing.add("retryTask");
        if (!log.available()) missing.add("logs");
        String id = "ev-" + (batchNo == null ? "unknown" : batchNo) + "-" + sequence;
        String hash = sha256(diffKey + '|' + redis + '|' + db + '|' + dlq + '|' + retry + '|' + log);
        boolean complete = redis.available() && db.available();
        return new EvidenceSnapshot(id, batchNo, diffKey, item.diffType(), item.userId(), item.blogId(),
                redis, db, dlq, retry, log, complete, List.copyOf(missing), hash,
                Instant.now(), (System.nanoTime() - started) / 1_000_000);
    }

    private EvidenceSnapshot.RedisEvidence redis(ReconcileDiffItem i) {
        long s = System.nanoTime();
        try { Map<String,Object> m = tools.queryThumbEvidence(i.userId(), i.blogId());
            return new EvidenceSnapshot.RedisEvidence(available(m), bool(m,"redisHasThumb"), str(m,"redisValue"),
                    num(m,"redisTtlSeconds"), elapsed(s));
        } catch (RuntimeException e) { return new EvidenceSnapshot.RedisEvidence(false,false,null,null,elapsed(s)); }
    }
    private EvidenceSnapshot.DbEvidence db(ReconcileDiffItem i) {
        long s = System.nanoTime();
        try { Map<String,Object> m = tools.queryThumbEvidence(i.userId(), i.blogId());
            return new EvidenceSnapshot.DbEvidence(available(m), bool(m,"dbThumbExists"), num(m,"dbThumbId"),
                    num(m,"blogThumbCount"), num(m,"actualDetailCount"), elapsed(s));
        } catch (RuntimeException e) { return new EvidenceSnapshot.DbEvidence(false,false,null,null,null,elapsed(s)); }
    }
    private EvidenceSnapshot.DlqEvidence dlq(ReconcileDiffItem i) {
        long s = System.nanoTime(); int scanned = 0;
        try { Map<String,Object> root = tools.queryDlqMessages(100); Object raw = root.get("items");
            if (!(raw instanceof List<?> list)) return new EvidenceSnapshot.DlqEvidence(true,false,null,null,null,null,Map.of(),0,elapsed(s));
            scanned = list.size();
            for (Object value : list) if (value instanceof Map<?,?> m) {
                String payload = str(m,"payload");
                if (contains(payload, i.userId()) && contains(payload, i.blogId()))
                    return new EvidenceSnapshot.DlqEvidence(true,true,str(m,"messageId"),str(m,"eventId"),payload,
                            str(m,"publishTime"), stringMap(m.get("properties")),scanned,elapsed(s));
            }
            return new EvidenceSnapshot.DlqEvidence(true,false,null,null,null,null,Map.of(),scanned,elapsed(s));
        } catch (RuntimeException e) { return new EvidenceSnapshot.DlqEvidence(false,false,null,null,null,null,Map.of(),scanned,elapsed(s)); }
    }
    private EvidenceSnapshot.RetryTaskEvidence retry(ReconcileDiffItem i, String bizKey) {
        long s = System.nanoTime();
        try { Map<String,Object> root = tools.queryRetryTasks(null,bizKey,null,10); Object raw=root.get("items");
            if (!(raw instanceof List<?> list) || list.isEmpty()) return new EvidenceSnapshot.RetryTaskEvidence(true,false,null,null,null,null,elapsed(s));
            Object v=list.get(0); if (!(v instanceof Map<?,?> m)) return new EvidenceSnapshot.RetryTaskEvidence(true,false,null,null,null,null,elapsed(s));
            return new EvidenceSnapshot.RetryTaskEvidence(true,true,str(m,"taskNo"),str(m,"status"),integer(m,"attempt"),str(m,"lastError"),elapsed(s));
        } catch (RuntimeException e) { return new EvidenceSnapshot.RetryTaskEvidence(false,false,null,null,null,null,elapsed(s)); }
    }
    private EvidenceSnapshot.LogEvidence logEvidence(ReconcileDiffItem i, String key) {
        long s=System.nanoTime(); try { LogSearchResult r=logs.search(key,"",null,null,10); List<String> samples=r.items().stream().map(LogSearchResult.Item::content).toList();
            String all=String.join("\n",samples); return new EvidenceSnapshot.LogEvidence(true,extract(all,"traceId"),extract(all,"eventId"),extract(all,"stage"),samples.stream().findFirst().orElse(null),samples,elapsed(s));
        } catch (RuntimeException e) { return new EvidenceSnapshot.LogEvidence(false,null,null,null,null,List.of(),elapsed(s)); }
    }
    private static boolean available(Map<String,Object> m){ return m != null && !Boolean.FALSE.equals(m.get("available")); }
    private static boolean bool(Map<?,?> m,String k){ Object v=m.get(k); return v instanceof Boolean b?b:Boolean.parseBoolean(String.valueOf(v)); }
    private static String str(Map<?,?> m,String k){ Object v=m.get(k); return v==null?null:String.valueOf(v); }
    private static Long num(Map<?,?> m,String k){ Object v=m.get(k); if(v instanceof Number n)return n.longValue(); try{return v==null?null:Long.valueOf(String.valueOf(v));}catch(Exception e){return null;} }
    private static Integer integer(Map<?,?> m,String k){Long n=num(m,k); return n==null?null:n.intValue();}
    private static long elapsed(long s){return (System.nanoTime()-s)/1_000_000;}
    private static boolean contains(String s,Object v){return s!=null&&v!=null&&s.contains(String.valueOf(v));}
    private static Map<String,String> stringMap(Object o){if(!(o instanceof Map<?,?> m))return Map.of(); Map<String,String> r=new LinkedHashMap<>();m.forEach((k,v)->r.put(String.valueOf(k),String.valueOf(v)));return r;}
    private static String extract(String text,String key){int p=text.indexOf(key); if(p<0)return null; int c=text.indexOf(':',p); if(c<0)return null; String v=text.substring(c+1).trim(); return v.replaceAll("^[\\\"']|[\\\"',} ].*$","");}
    private static String sha256(String value){try{byte[] b=MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));StringBuilder s=new StringBuilder();for(byte x:b)s.append(String.format("%02x",x));return s.toString();}catch(Exception e){throw new IllegalStateException(e);}}
}
