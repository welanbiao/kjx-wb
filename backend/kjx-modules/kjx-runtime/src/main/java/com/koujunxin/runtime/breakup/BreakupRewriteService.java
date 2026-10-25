package com.koujunxin.runtime.breakup;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.koujunxin.runtime.api.RuntimeAiPort;
import com.koujunxin.runtime.api.RuntimeFailure;
import com.koujunxin.runtime.api.dto.SessionRef;
import com.koujunxin.runtime.api.dto.ToolExecutionCommand;
import com.koujunxin.runtime.api.dto.ToolResult;
import com.koujunxin.runtime.api.port.AccountAiSettingsPort;
import com.koujunxin.runtime.api.port.ToolExecutionPort;
import com.koujunxin.runtime.ledger.CanonicalJson;

public final class BreakupRewriteService {

    private final BreakupCatalog catalog;
    private final ToolExecutionPort tools;
    private final Supplier<RuntimeAiPort> aiSource;
    private final AccountAiSettingsPort settings;

    public BreakupRewriteService(BreakupCatalog catalog, ToolExecutionPort tools, RuntimeAiPort ai) {
        this(catalog, tools, ai, null);
    }

    public BreakupRewriteService(
            BreakupCatalog catalog,
            ToolExecutionPort tools,
            RuntimeAiPort ai,
            AccountAiSettingsPort settings) {
        this(catalog, tools, () -> ai, settings);
    }

    public BreakupRewriteService(
            BreakupCatalog catalog,
            ToolExecutionPort tools,
            Supplier<RuntimeAiPort> aiSource,
            AccountAiSettingsPort settings) {
        this.catalog = catalog;
        this.tools = tools;
        this.aiSource = aiSource == null ? () -> null : aiSource;
        this.settings = settings;
    }

    public RewriteResult rewrite(
            long accountId,
            String sessionId,
            ObjectNode session,
            String nodeId,
            String msgId,
            String newText,
            String apiKey,
            String apiBase,
            String apiModel) {
        assertRewrittenWorld(session);
        msgId = BreakupPackEngine.normalizeMessageId(msgId);
        ObjectNode bundle = (ObjectNode) session.get("rewritten");
        if (BreakupPackEngine.computeReplayBoundary(bundle, nodeId, msgId) == null) {
            throw RuntimeFailure.badRequest("PACK_INVALID", "找不到改写边界");
        }
        boolean trial = session.path("trial").asBoolean(false);
        if (trial) {
            return trialRewrite(session, nodeId, msgId, newText);
        }
        AccountAiSettingsPort.Snapshot resolved = resolveSettings(accountId, apiKey, apiBase, apiModel);
        return fullRewrite(
                accountId,
                sessionId,
                session,
                nodeId,
                msgId,
                newText,
                resolved.apiKey(),
                resolved.baseUrl(),
                resolved.model());
    }

    public RewriteResult send(
            long accountId,
            String sessionId,
            ObjectNode session,
            String text,
            String apiKey,
            String apiBase,
            String apiModel) {
        assertRewrittenWorld(session);
        if (session.path("trial").asBoolean(false)) {
            throw RuntimeFailure.badRequest("TRIAL_UNCOVERED", BreakupWorks.DEMO_UNCOVERED);
        }
        if (text == null || text.isBlank()) {
            throw RuntimeFailure.badRequest("PACK_INVALID", "正文不能为空");
        }
        seedPersona(session);
        AccountAiSettingsPort.Snapshot resolved = resolveSettings(accountId, apiKey, apiBase, apiModel);
        requireLiveModel(resolved);
        String raw = liveAi().complete(
                accountId,
                sessionId,
                BreakupWorks.CODE,
                userPayload(session, "SEND " + text, resolved.apiKey(), resolved.baseUrl(), resolved.model()));
        rejectIfKeyLeaked(raw, resolved.apiKey());
        BreakupPackEngine.rejectAiFailure(raw);
        BreakupPackEngine.ParsedModel parsed = BreakupPackEngine.parseModelOutput(raw);
        if (parsed.pack() != null && parsed.pack().has("from")) {
            throw RuntimeFailure.badRequest("PACK_INVALID", "发送不得改标题或重写历史 PACK");
        }
        List<String> replies = new ArrayList<>(BreakupPackEngine.repliesOrPlain(raw, parsed.replies()));
        if (replies.isEmpty()) {
            throw RuntimeFailure.badRequest("AI_FAILED", "模型没有返回对白");
        }
        BreakupPackEngine.appendSendTurn(session, text, replies);
        BreakupWorldRules.apply(session, text);
        return new RewriteResult(session, replies, "", false);
    }

    private RewriteResult trialRewrite(ObjectNode session, String nodeId, String msgId, String newText) {
        JsonNode line = findTrialLine(msgId);
        if (line == null) {
            throw RuntimeFailure.badRequest("TRIAL_UNCOVERED", BreakupWorks.DEMO_UNCOVERED);
        }
        JsonNode soft = line.path("soft");
        JsonNode hard = line.path("hard");
        ObjectNode pack;
        String tone = BreakupWorldRules.tone(newText);
        boolean useSoft = "soft".equals(tone)
                && soft != null
                && !soft.isMissingNode()
                && !soft.path("useOriginal").asBoolean(false);
        if (useSoft) {
            pack = BreakupPackEngine.packFromBranch(soft);
        } else {
            pack = BreakupPackEngine.packFromOriginal(session.path("rewritten"), nodeId, msgId, hard);
        }
        if (pack == null) {
            throw RuntimeFailure.badRequest("PACK_INVALID", "演示剧本不完整");
        }
        BreakupPackEngine.validateEditPack(session.path("rewritten"), pack, nodeId, msgId);
        BreakupPackEngine.rewriteMessageText(session, nodeId, msgId, newText);
        BreakupPackEngine.applyPack(session, pack, nodeId, msgId);
        boolean worldMoved = BreakupWorldRules.apply(session, newText);
        recordEdit(session, nodeId, msgId, newText, true);
        String ripple = useSoft ? soft.path("nar").asText("") : hard.path("nar").asText("");
        return new RewriteResult(session, List.of(), ripple, worldMoved);
    }

    private RewriteResult fullRewrite(
            long accountId,
            String sessionId,
            ObjectNode session,
            String nodeId,
            String msgId,
            String newText,
            String apiKey,
            String apiBase,
            String apiModel) {
        seedPersona(session);
        requireLiveModel(new AccountAiSettingsPort.Snapshot(apiBase, apiKey, apiModel));
        String raw = liveAi().complete(
                accountId,
                sessionId,
                BreakupWorks.CODE,
                userPayload(session, "EDIT node=" + nodeId + " msg=" + msgId + " text=" + newText, apiKey, apiBase, apiModel));
        rejectIfKeyLeaked(raw, apiKey);
        BreakupPackEngine.rejectAiFailure(raw);
        BreakupPackEngine.ParsedModel parsed = BreakupPackEngine.parseModelOutput(raw);
        if (parsed.pack() == null) {
            throw RuntimeFailure.badRequest("PACK_INVALID", "推演结果不完整，未写入改写");
        }
        if (parsed.pack().has("title") || parsed.pack().has("bridgeNarr")) {
            throw RuntimeFailure.badRequest("PACK_INVALID", "标题冻结，本步拒绝");
        }
        ObjectNode before = session.deepCopy();
        try {
            BreakupPackEngine.validateEditPack(session.path("rewritten"), parsed.pack(), nodeId, msgId);
            BreakupPackEngine.rewriteMessageText(session, nodeId, msgId, newText);
            BreakupPackEngine.applyPack(session, parsed.pack(), nodeId, msgId);
            BreakupPackEngine.applyState(session, parsed.state());
            if (!BreakupPackEngine.titlesIntact(session)) {
                throw RuntimeFailure.badRequest("PACK_INVALID", "标题冻结，本步拒绝");
            }
        } catch (RuntimeFailure ex) {
            session.removeAll();
            session.setAll(before);
            throw ex;
        }
        boolean worldMoved = BreakupWorldRules.apply(session, newText);
        recordEdit(session, nodeId, msgId, newText, false);
        BreakupPackEngine.appendModelReplies(session, parsed.replies());
        return new RewriteResult(session, parsed.replies(), parsed.ripple(), worldMoved);
    }

    private RuntimeAiPort liveAi() {
        return aiSource.get();
    }

    private void requireLiveModel(AccountAiSettingsPort.Snapshot resolved) {
        if (resolved == null || resolved.apiKey() == null || resolved.apiKey().isBlank()
                || resolved.baseUrl() == null || resolved.baseUrl().isBlank()) {
            throw RuntimeFailure.badRequest("KEY_REQUIRED", "完整版需要密钥");
        }
        if (liveAi() == null) {
            throw RuntimeFailure.badRequest("AI_UNAVAILABLE", "完整版需要模型通道");
        }
    }

    public void assemblePersona(long accountId, String sessionId, ObjectNode session) {
        seedPersona(session);
        if (tools == null) {
            return;
        }
        if (session.path("trial").asBoolean(false)) {
            return;
        }
        ToolResult result = tools.execute(new ToolExecutionCommand(
                "persona.assemble",
                "1",
                new SessionRef(sessionId, BreakupWorks.CODE),
                accountId,
                BreakupWorks.CODE,
                sessionId + ":persona.assemble:" + UUID.randomUUID().toString().replace("-", "").substring(0, 8),
                "{}"));
        if (!result.success()) {
            throw RuntimeFailure.badRequest(result.code(), result.message());
        }
    }

    public void callChatSend(long accountId, String sessionId, ObjectNode session, String content) {
        if (session.path("trial").asBoolean(false) || tools == null || content == null || content.isBlank()) {
            return;
        }
        ObjectNode args = CanonicalJson.MAPPER.createObjectNode();
        args.put("content", content);
        ToolResult result = tools.execute(new ToolExecutionCommand(
                "chat.send",
                "1",
                new SessionRef(sessionId, BreakupWorks.CODE),
                accountId,
                BreakupWorks.CODE,
                sessionId + ":chat.send:" + UUID.randomUUID().toString().replace("-", "").substring(0, 12),
                CanonicalJson.write(args)));
        if (!result.success()) {
            throw RuntimeFailure.badRequest(result.code(), result.message());
        }
    }

    public void callRedpacket(long accountId, String sessionId, ObjectNode session, double amount, String note) {
        if (session.path("trial").asBoolean(false) || tools == null) {
            throw RuntimeFailure.badRequest("TRIAL_UNCOVERED", BreakupWorks.DEMO_UNCOVERED);
        }
        ObjectNode args = CanonicalJson.MAPPER.createObjectNode();
        args.put("amount", amount);
        args.put("note", note == null ? "" : note);
        ToolResult result = tools.execute(new ToolExecutionCommand(
                "redpacket.send",
                "1",
                new SessionRef(sessionId, BreakupWorks.CODE),
                accountId,
                BreakupWorks.CODE,
                sessionId + ":redpacket.send:" + UUID.randomUUID().toString().replace("-", "").substring(0, 12),
                CanonicalJson.write(args)));
        if (!result.success()) {
            throw RuntimeFailure.badRequest(result.code(), result.message());
        }
    }

    static void seedPersona(ObjectNode session) {
        JsonNode couple = session.path("rewritten");
        // persona lives on catalog characters; rewrite service receives them via session.persona seed
        if (session.path("persona").path("outer").isTextual()) {
            return;
        }
        ObjectNode persona = session.with("persona");
        persona.put("outer", session.path("otherOuter").asText("话少。"));
        persona.put("inner", session.path("otherInner").asText("不会表达。"));
        persona.put("origin", session.path("otherOrigin").asText("自己扛。"));
        persona.put("speech", session.path("otherSpeech").asText("极简。"));
    }

    public void seedCharacters(ObjectNode session, JsonNode couple, String side) {
        JsonNode me = "b".equals(side) ? couple.path("b") : couple.path("a");
        JsonNode other = "b".equals(side) ? couple.path("a") : couple.path("b");
        session.put("side", side);
        session.put("meName", me.path("name").asText());
        session.put("otherName", other.path("name").asText());
        ObjectNode persona = session.with("persona");
        persona.put("outer", other.path("outer").asText());
        persona.put("inner", other.path("inner").asText());
        persona.put("origin", other.path("origin").asText());
        persona.put("speech", other.path("speech").asText());
        persona.put("triggers", "");
        persona.put("redlines", "不把精确爱意或分手概率当台词重点。不改 title 与 bridgeNarr。");
        StringBuilder fragment = new StringBuilder();
        fragment.append("outer: ").append(persona.path("outer").asText()).append('\n');
        fragment.append("inner: ").append(persona.path("inner").asText()).append('\n');
        fragment.append("origin: ").append(persona.path("origin").asText()).append('\n');
        fragment.append("speech: ").append(persona.path("speech").asText()).append('\n');
        fragment.append("redlines: ").append(persona.path("redlines").asText());
        session.with("prompt").put("persona", fragment.toString());
    }

    private JsonNode findTrialLine(String msgId) {
        for (JsonNode line : catalog.trialScript().path("lines")) {
            if (msgId.equals(line.path("messageId").asText())) {
                return line;
            }
        }
        return null;
    }

    private static void assertRewrittenWorld(ObjectNode session) {
        if (!"rewritten".equals(session.path("worldline").asText())) {
            throw RuntimeFailure.badRequest("ORIGINAL_LOCKED", BreakupWorks.ORIGINAL_EDIT);
        }
    }

    private static void recordEdit(ObjectNode session, String nodeId, String msgId, String text, boolean trial) {
        ArrayNode edits = session.withArray("edits");
        ObjectNode row = CanonicalJson.MAPPER.createObjectNode();
        row.put("nodeId", nodeId);
        row.put("msgId", msgId);
        row.put("text", text);
        row.put("trial", trial);
        edits.add(row);
        if (!trial) {
            session.withArray("achievements");
        }
    }

    private AccountAiSettingsPort.Snapshot resolveSettings(
            long accountId, String apiKey, String apiBase, String apiModel) {
        AccountAiSettingsPort.Snapshot saved = settings == null ? null : settings.find(accountId).orElse(null);
        return new AccountAiSettingsPort.Snapshot(
                firstNonBlank(saved == null ? null : saved.baseUrl(), apiBase),
                firstNonBlank(saved == null ? null : saved.apiKey(), apiKey),
                firstNonBlank(saved == null ? null : saved.model(), apiModel));
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second == null ? "" : second;
    }

    private static String userPayload(
            ObjectNode session, String action, String apiKey, String apiBase, String apiModel) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("channel", "BYOK");
        body.put("text", action);
        body.put("content", action);
        if (apiBase != null && !apiBase.isBlank()) {
            body.put("baseUrl", apiBase);
            body.put("endpoint", apiBase);
        }
        if (apiModel != null && !apiModel.isBlank()) {
            body.put("model", apiModel);
        }
        if (apiKey != null && !apiKey.isBlank()) {
            body.put("apiKey", apiKey);
        }
        ObjectNode state = CanonicalJson.MAPPER.createObjectNode();
        state.set("prompt", session.path("prompt").deepCopy());
        state.put("bp", session.path("bp").asInt());
        state.put("lm", session.path("lm").asInt());
        state.put("lf", session.path("lf").asInt());
        state.put("side", session.path("side").asText());
        state.put("worldline", session.path("worldline").asText());
        state.put("frontier", session.path("frontier").asText(""));
        state.put("coupleLabel", BreakupWorks.COUPLE_LABEL);
        body.put("state", state);
        return CanonicalJson.write(CanonicalJson.MAPPER.valueToTree(body));
    }

    private static void rejectIfKeyLeaked(String raw, String apiKey) {
        if (apiKey != null && !apiKey.isBlank() && raw != null && raw.contains(apiKey)) {
            throw RuntimeFailure.badRequest("PACK_INVALID", "模型输出非法");
        }
    }

    public record RewriteResult(ObjectNode session, List<String> replies, String ripple, boolean worldMoved) {
    }
}
