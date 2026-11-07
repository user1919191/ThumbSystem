package com.example.demo.Agent.Reconcile;

import com.example.demo.compensation.ReconcileDiffItem;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

/** LLM diagnosis only. It has no write tools and always has a deterministic fallback. */
@Service
@RequiredArgsConstructor
public class ReconcileAgent {
    private static final String PROMPT_VERSION = "reconcile-v1";
    private final ObjectProvider<ChatClient> chatClients;
    private final ObjectProvider<ReconcileReadTools> readTools;
    private final ObjectMapper objectMapper;

    public AgentDiagnosis diagnose(ReconcileDiffItem item, EvidenceSnapshot snapshot) {
        long started = System.currentTimeMillis();
        ChatClient client = chatClients.getIfAvailable();
        if (client == null) return fallback(item, snapshot, "CHAT_CLIENT_UNAVAILABLE", started);
        try {
            String prompt = systemPrompt() + "\n\n" + userPrompt(item, snapshot);
            ChatClient.ChatClientRequestSpec request = client.prompt().user(prompt)
                    .options(ChatOptions.builder().temperature(0.0).maxTokens(1200).build());
            ReconcileReadTools tools = readTools.getIfAvailable();
            if (tools != null) request = request.tools(tools);
            String raw = request.call().content();
            AgentDiagnosis parsed = parse(raw, snapshot, started);
            return parsed == null ? fallback(item, snapshot, "INVALID_JSON", started) : parsed;
        } catch (Exception e) {
            return fallback(item, snapshot, "LLM_CALL_FAILED", started);
        }
    }

    private AgentDiagnosis parse(String raw, EvidenceSnapshot snapshot, long started) {
        if (raw == null || raw.isBlank()) return null;
        try {
            String json = raw.trim();
            if (json.startsWith("``")) json = json.replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", "");
            JsonNode n = objectMapper.readTree(json);
            String classification = text(n,"classification");
            String action = text(n,"proposedAction");
            double confidence = n.path("confidence").asDouble(-1);
            List<AgentDiagnosis.EvidenceRef> refs = new ArrayList<>();
            if (n.path("evidenceRefs").isArray()) for (JsonNode r : n.path("evidenceRefs")) refs.add(new AgentDiagnosis.EvidenceRef(text(r,"evidenceId"),text(r,"field"),text(r,"value"),text(r,"meaning")));
            List<String> risks = new ArrayList<>(); if(n.path("risks").isArray()) n.path("risks").forEach(v->risks.add(v.asText()));
            Map<String,Object> params = n.has("actionParams") && n.path("actionParams").isObject() ? objectMapper.convertValue(n.path("actionParams"), Map.class) : Map.of();
            return new AgentDiagnosis(snapshot.evidenceId(), classification, confidence, text(n,"summary"), refs, action, params, risks, n.path("needHuman").asBoolean(false),
                    new AgentDiagnosis.AgentTrace("configured", PROMPT_VERSION, 1, List.of(), 0, 0, System.currentTimeMillis()-started, raw, "OK"));
        } catch (Exception e) { return null; }
    }

    private AgentDiagnosis fallback(ReconcileDiffItem item, EvidenceSnapshot s, String reason, long started) {
        String c; String action; String summary;
        if (!s.complete()) { c="A4"; action="ESCALATE"; summary="证据不完整，无法安全判断：" + reason; }
        else if (s.redis().hasThumb() && s.db().thumbExists()) { c="A2"; action="SKIP"; summary="Redis 与 DB 均存在点赞明细，按幂等成功处理"; }
        else if (s.redis().hasThumb() && !s.db().thumbExists()) { c=item.diffType()==com.example.demo.compensation.ReconcileDiffType.REDIS_ONLY?"A5":"A3"; action="WRITE_DB"; summary="Redis 有状态但 DB 缺少明细"; }
        else if (!s.redis().hasThumb() && s.db().thumbExists()) { c="A6"; action="ESCALATE"; summary="DB 有明细但 Redis 缺失，仅告警并人工确认"; }
        else { c="A4"; action="ESCALATE"; summary="Redis 与 DB 均无对应状态，缺少可用上下文"; }
        List<AgentDiagnosis.EvidenceRef> refs = List.of(ref(s,c.startsWith("A6")?"db.thumbExists":"redis.available"));
        return new AgentDiagnosis(s.evidenceId(),c,0.5,summary,refs,action,Map.of("userId",item.userId(),"blogId",item.blogId()),List.of(reason),true,
                new AgentDiagnosis.AgentTrace("rules",PROMPT_VERSION,0,List.of(),0,0,System.currentTimeMillis()-started,reason,"RULE_FALLBACK"));
    }
    private AgentDiagnosis.EvidenceRef ref(EvidenceSnapshot s,String field){Object value=snapshotField(s,field);return new AgentDiagnosis.EvidenceRef(s.evidenceId(),field,String.valueOf(value),"rule evidence");}
    private Object snapshotField(EvidenceSnapshot s,String f){return switch(f){case "redis.available"->s.redis().available();case "db.thumbExists"->s.db().thumbExists();default->null;};}
    private String userPrompt(ReconcileDiffItem i,EvidenceSnapshot s){try{return "Analyze one reconcile diff. Return JSON only. diff="+objectMapper.writeValueAsString(i)+" evidence="+objectMapper.writeValueAsString(s);}catch(Exception e){return "diffKey="+i.diffKey()+" evidenceId="+s.evidenceId();}}
    private String systemPrompt(){return "You are a read-only reconciliation diagnosis agent. Never execute writes. Use only evidence and available read tools. Missing evidence is not false. Output JSON fields: classification(A1..A8), confidence, summary, evidenceRefs[{evidenceId,field,value,meaning}], proposedAction(WRITE_DB|REBUILD_REDIS|DELETE_REDIS|RECOMPUTE_COUNT|SKIP|ESCALATE), actionParams, risks, needHuman.";}
    private static String text(JsonNode n,String field){JsonNode v=n.get(field);return v==null||v.isNull()?null:v.asText();}
}
