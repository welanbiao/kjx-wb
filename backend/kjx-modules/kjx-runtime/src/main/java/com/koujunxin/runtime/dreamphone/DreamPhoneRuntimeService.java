package com.koujunxin.runtime.dreamphone;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.koujunxin.runtime.api.RuntimeAiPort;
import com.koujunxin.runtime.api.RuntimeFailure;
import com.koujunxin.runtime.api.dto.SessionRef;
import com.koujunxin.runtime.api.dto.StateMutation;
import com.koujunxin.runtime.api.dto.ToolExecutionCommand;
import com.koujunxin.runtime.api.dto.ToolResult;
import com.koujunxin.runtime.api.port.AccountAiSettingsPort;
import com.koujunxin.runtime.api.port.EntitlementAccessPort;
import com.koujunxin.runtime.api.port.ToolExecutionPort;
import com.koujunxin.runtime.ledger.CanonicalJson;
import com.koujunxin.runtime.session.RuntimeStore;
import com.koujunxin.runtime.session.SessionRecord;
import com.koujunxin.runtime.session.SessionStateService;

public final class DreamPhoneRuntimeService {

    private static final String USER_LINE = "\u8FD8\u597D\uFF0C\u521A\u4E0B\u73ED";
    private static final String SCRIPT_REPLY = "\u90A3\u5C31\u597D\u3002\u522B\u997F\u7740\u3002";
    private static final Set<String> SCREENS = Set.of(
            "pageLauncher",
            "pageWechatMain",
            "pageWechatContacts",
            "pageWechatDiscover",
            "pageWechatMe",
            "pageChat",
            "pageMoments",
            "pageMemo",
            "pageGallery",
            "pageWallet",
            "pageSettings",
            "pageProfile",
            "pageCreateChar",
            "pageGameSelect",
            "pageGomoku",
            "pageBaoleme",
            "pageJingxi",
            "pageKumao",
            "callPage");

    private final DreamPhoneSessionFactory factory;
    private final SessionStateService sessions;
    private final RuntimeStore store;
    private final EntitlementAccessPort entitlements;
    private final ToolExecutionPort tools;
    private final RuntimeAiPort ai;
    private final AccountAiSettingsPort settings;

    public DreamPhoneRuntimeService(
            DreamPhoneSessionFactory factory,
            SessionStateService sessions,
            RuntimeStore store,
            EntitlementAccessPort entitlements,
            ToolExecutionPort tools,
            RuntimeAiPort ai,
            AccountAiSettingsPort settings) {
        this.factory = factory;
        this.sessions = sessions;
        this.store = store;
        this.entitlements = entitlements;
        this.tools = tools;
        this.ai = ai;
        this.settings = settings;
    }

    public Map<String, Object> view(long accountId, String sessionId) {
        ObjectNode payload = loadOwned(accountId, sessionId);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("sessionId", sessionId);
        body.put("workCode", DreamPhoneWorks.CODE);
        body.put("trial", payload.path("trial").asBoolean(false));
        body.put("playerName", payload.path("playerName").asText(DreamPhoneWorks.PLAYER));
        body.put("characterName", payload.path("characterName").asText(DreamPhoneWorks.CHARACTER));
        body.put("uncovered", DreamPhoneWorks.UNCOVERED);
        body.put("payload", CanonicalJson.parse(CanonicalJson.write(payload)));
        return body;
    }

    public Map<String, Object> action(long accountId, String sessionId, Map<String, Object> body) {
        ObjectNode session = loadOwned(accountId, sessionId);
        String type = string(body, "type");
        switch (type) {
            case "set-screen" -> {
                String screen = string(body, "screen");
                if (!SCREENS.contains(screen)) {
                    throw RuntimeFailure.badRequest("INVALID_MUTATION", "unknown screen");
                }
                if ("pageChat".equals(screen) && body.containsKey("charId")) {
                    session.put("currentCharId", string(body, "charId"));
                }
                session.put("screen", screen);
                persist(sessionId, session);
            }
            case "send" -> send(accountId, sessionId, session, string(body, "text"));
            case "post-moment" -> postMoment(accountId, sessionId, session, string(body, "text"));
            case "like-moment" -> likeMoment(sessionId, session, string(body, "momentId"));
            case "comment-moment" -> commentMoment(sessionId, session, string(body, "momentId"), string(body, "text"));
            case "redpacket" -> spend(accountId, sessionId, session, "redpacket", number(body, "amount"), optional(body, "note"));
            case "transfer" -> spend(accountId, sessionId, session, "transfer", number(body, "amount"), optional(body, "note"));
            case "write-memo" -> writeMemo(accountId, sessionId, session, string(body, "text"));
            case "delete-memo" -> deleteMemo(sessionId, session, string(body, "memoId"));
            case "add-photo" -> addPhoto(sessionId, session, optional(body, "desc"), optional(body, "url"));
            case "create-char" -> createChar(sessionId, session, string(body, "name"), optional(body, "emoji"), optional(body, "color"));
            case "delete-char" -> deleteChar(sessionId, session, string(body, "charId"));
            case "set-font-scale" -> {
                session.put("fontScale", string(body, "scale"));
                persist(sessionId, session);
            }
            case "complete-setup" -> {
                session.put("setupDone", true);
                persist(sessionId, session);
            }
            case "set-chat-bg" -> {
                session.put("chatBg", string(body, "bg"));
                persist(sessionId, session);
            }
            case "regen" -> regen(accountId, sessionId, session);
            case "game-result" -> gameResult(sessionId, session, string(body, "game"), string(body, "result"));
            case "place-order" -> placeOrder(sessionId, session, string(body, "app"), string(body, "title"), number(body, "amount"));
            case "call-end" -> callEnd(sessionId, session, optional(body, "note"));
            case "reset" -> {
                String fresh = factory.initialPayload(session.path("trial").asBoolean(false));
                persist(sessionId, (ObjectNode) CanonicalJson.parse(fresh));
            }
            default -> throw RuntimeFailure.badRequest("INVALID_MUTATION", "unknown dream-phone action");
        }
        return view(accountId, sessionId);
    }

    String initialPayload(boolean trial) {
        return factory.initialPayload(trial);
    }

    private void send(long accountId, String sessionId, ObjectNode session, String text) {
        String charId = session.path("currentCharId").asText(DreamPhoneWorks.CHARACTER_ID);
        if (session.path("trial").asBoolean(false)) {
            if (session.path("trialStep").asInt(0) != 0 || !USER_LINE.equals(text)) {
                throw RuntimeFailure.badRequest("TRIAL_UNCOVERED", DreamPhoneWorks.UNCOVERED);
            }
            pushChat(session, charId, "user", "text", text);
            pushChat(session, charId, "assistant", "text", SCRIPT_REPLY);
            session.put("trialStep", 1);
            session.put("screen", "pageChat");
            persist(sessionId, session);
            return;
        }
        AccountAiSettingsPort.Snapshot saved = settings == null ? null : settings.find(accountId).orElse(null);
        if (saved == null || blank(saved.apiKey()) || blank(saved.baseUrl())) {
            throw RuntimeFailure.badRequest("KEY_REQUIRED", "\u5B8C\u6574\u7248\u9700\u8981\u5BC6\u94A5");
        }
        if (ai == null) {
            throw RuntimeFailure.badRequest("AI_UNAVAILABLE", "\u5B8C\u6574\u7248\u9700\u8981\u6A21\u578B\u901A\u9053");
        }
        pushChat(session, charId, "user", "text", text);
        session.put("screen", "pageChat");
        persist(sessionId, session);
        callTool(accountId, sessionId, "persona.assemble", "{}");
        String reply = ai.complete(accountId, sessionId, DreamPhoneWorks.CODE, aiPayload(loadOwned(accountId, sessionId), text, saved));
        if (blank(reply)) {
            throw RuntimeFailure.badRequest("AI_UNAVAILABLE", "\u6A21\u578B\u6CA1\u6709\u8FD4\u56DE\u5185\u5BB9");
        }
        String spoken = reply.trim();
        callTool(accountId, sessionId, "chat.send", contentArgs(spoken));
        ObjectNode fresh = loadOwned(accountId, sessionId);
        pushChat(fresh, charId, "assistant", "text", spoken);
        fresh.put("screen", "pageChat");
        persist(sessionId, fresh);
    }

    private void postMoment(long accountId, String sessionId, ObjectNode session, String text) {
        if (session.path("trial").asBoolean(false)) {
            throw RuntimeFailure.badRequest("TRIAL_UNCOVERED", DreamPhoneWorks.UNCOVERED);
        }
        ObjectNode moment = CanonicalJson.MAPPER.createObjectNode();
        moment.put("id", "m_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10));
        moment.put("charId", "user");
        moment.put("author", session.path("playerName").asText(DreamPhoneWorks.PLAYER));
        moment.put("charEmoji", "\uD83D\uDC64");
        moment.put("content", text);
        moment.put("time", System.currentTimeMillis());
        moment.put("liked", false);
        moment.set("likes", CanonicalJson.MAPPER.createArrayNode());
        moment.set("comments", CanonicalJson.MAPPER.createArrayNode());
        moment.set("images", CanonicalJson.MAPPER.createArrayNode());
        session.withArray("moments").insert(0, moment);
        session.put("momentCount", session.path("moments").size());
        session.put("screen", "pageMoments");
        persist(sessionId, session);
        callTool(accountId, sessionId, "moments.post", contentArgs(text));
    }

    private void likeMoment(String sessionId, ObjectNode session, String momentId) {
        for (JsonNode node : session.path("moments")) {
            if (momentId.equals(node.path("id").asText()) && node instanceof ObjectNode row) {
                boolean liked = !row.path("liked").asBoolean(false);
                row.put("liked", liked);
                ArrayNode likes = row.withArray("likes");
                likes.removeAll();
                if (liked) {
                    likes.add(session.path("playerName").asText(DreamPhoneWorks.PLAYER));
                }
                break;
            }
        }
        persist(sessionId, session);
    }

    private void commentMoment(String sessionId, ObjectNode session, String momentId, String text) {
        for (JsonNode node : session.path("moments")) {
            if (momentId.equals(node.path("id").asText()) && node instanceof ObjectNode row) {
                ObjectNode comment = CanonicalJson.MAPPER.createObjectNode();
                comment.put("name", session.path("playerName").asText(DreamPhoneWorks.PLAYER));
                comment.put("text", text);
                row.withArray("comments").add(comment);
                break;
            }
        }
        persist(sessionId, session);
    }

    private void spend(long accountId, String sessionId, ObjectNode session, String kind, double amount, String note) {
        if (session.path("trial").asBoolean(false)) {
            throw RuntimeFailure.badRequest("TRIAL_UNCOVERED", DreamPhoneWorks.UNCOVERED);
        }
        if (amount <= 0) {
            throw RuntimeFailure.badRequest("INVALID_MUTATION", "amount must be positive");
        }
        double wallet = session.path("wallet").asDouble(DreamPhoneWorks.WALLET);
        if (amount > wallet) {
            throw RuntimeFailure.badRequest("WALLET_SHORT", "\u94B1\u4E0D\u591F");
        }
        String label = blank(note) ? kind : note;
        ObjectNode args = CanonicalJson.MAPPER.createObjectNode();
        args.put("amount", amount);
        args.put("note", label);
        if ("transfer".equals(kind)) {
            ObjectNode adjust = CanonicalJson.MAPPER.createObjectNode();
            adjust.put("delta", -amount);
            adjust.put("reason", label);
            callTool(accountId, sessionId, "wallet.adjust", CanonicalJson.write(adjust));
        } else {
            callTool(accountId, sessionId, "redpacket.send", CanonicalJson.write(args));
        }
        ObjectNode fresh = loadOwned(accountId, sessionId);
        if (fresh.path("wallet").asDouble(wallet) == wallet) {
            fresh.put("wallet", wallet - amount);
        }
        ObjectNode tx = CanonicalJson.MAPPER.createObjectNode();
        tx.put("title", label);
        tx.put("amount", -amount);
        tx.put("time", System.currentTimeMillis());
        fresh.withArray("walletLedger").insert(0, tx);
        String charId = fresh.path("currentCharId").asText(DreamPhoneWorks.CHARACTER_ID);
        ObjectNode card = CanonicalJson.MAPPER.createObjectNode();
        card.put("role", "user");
        card.put("type", kind);
        card.put("content", label);
        card.put("time", System.currentTimeMillis());
        card.put("amount", amount);
        card.put("opened", false);
        thread(fresh, charId).add(card);
        ObjectNode tip = CanonicalJson.MAPPER.createObjectNode();
        tip.put("role", "assistant");
        tip.put("type", kind);
        tip.put("content", "\u6536\u5230\u4E86\uFF0C\u8C22\u8C22");
        tip.put("time", System.currentTimeMillis());
        tip.put("amount", amount);
        tip.put("opened", false);
        thread(fresh, charId).add(tip);
        fresh.put("screen", "pageChat");
        persist(sessionId, fresh);
    }

    private void regen(long accountId, String sessionId, ObjectNode session) {
        String charId = session.path("currentCharId").asText(DreamPhoneWorks.CHARACTER_ID);
        ArrayNode thread = thread(session, charId);
        int lastAssistant = -1;
        for (int i = thread.size() - 1; i >= 0; i--) {
            JsonNode row = thread.get(i);
            if ("assistant".equals(row.path("role").asText()) && "text".equals(row.path("type").asText("text"))) {
                lastAssistant = i;
                break;
            }
        }
        if (lastAssistant < 0) {
            throw RuntimeFailure.badRequest("INVALID_MUTATION", "no reply to regenerate");
        }
        if (session.path("trial").asBoolean(false)) {
            ((ObjectNode) thread.get(lastAssistant)).put("content", SCRIPT_REPLY);
            persist(sessionId, session);
            return;
        }
        AccountAiSettingsPort.Snapshot saved = settings == null ? null : settings.find(accountId).orElse(null);
        if (saved == null || blank(saved.apiKey()) || blank(saved.baseUrl()) || ai == null) {
            throw RuntimeFailure.badRequest("KEY_REQUIRED", "\u5B8C\u6574\u7248\u9700\u8981\u5BC6\u94A5");
        }
        String seed = "\u8BF7\u6362\u4E00\u79CD\u8BF4\u6CD5\u518D\u56DE\u4E00\u53E5";
        String reply = ai.complete(accountId, sessionId, DreamPhoneWorks.CODE, aiPayload(session, seed, saved));
        if (blank(reply)) {
            throw RuntimeFailure.badRequest("AI_UNAVAILABLE", "\u6A21\u578B\u6CA1\u6709\u8FD4\u56DE\u5185\u5BB9");
        }
        ObjectNode row = (ObjectNode) thread.get(lastAssistant);
        ArrayNode versions = row.has("versions") && row.get("versions").isArray()
                ? (ArrayNode) row.get("versions")
                : CanonicalJson.MAPPER.createArrayNode();
        if (versions.size() == 0) {
            versions.add(row.path("content").asText());
        }
        String spoken = reply.trim();
        versions.add(spoken);
        row.set("versions", versions);
        row.put("content", spoken);
        row.put("versionIndex", versions.size() - 1);
        persist(sessionId, session);
    }

    private void writeMemo(long accountId, String sessionId, ObjectNode session, String text) {
        if (session.path("trial").asBoolean(false)) {
            throw RuntimeFailure.badRequest("TRIAL_UNCOVERED", DreamPhoneWorks.UNCOVERED);
        }
        ObjectNode row = CanonicalJson.MAPPER.createObjectNode();
        row.put("id", "memo_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8));
        row.put("day", "today");
        row.put("charId", session.path("currentCharId").asText(DreamPhoneWorks.CHARACTER_ID));
        row.put("content", text);
        row.put("time", System.currentTimeMillis());
        session.withArray("memos").insert(0, row);
        session.put("screen", "pageMemo");
        persist(sessionId, session);
        ObjectNode args = CanonicalJson.MAPPER.createObjectNode();
        args.put("title", text);
        args.put("body", text);
        callTool(accountId, sessionId, "memo.write", CanonicalJson.write(args));
    }

    private void deleteMemo(String sessionId, ObjectNode session, String memoId) {
        ArrayNode memos = session.withArray("memos");
        ArrayNode next = CanonicalJson.MAPPER.createArrayNode();
        for (JsonNode memo : memos) {
            if (!memoId.equals(memo.path("id").asText())) {
                next.add(memo);
            }
        }
        session.set("memos", next);
        persist(sessionId, session);
    }

    private void addPhoto(String sessionId, ObjectNode session, String desc, String url) {
        if (session.path("trial").asBoolean(false)) {
            throw RuntimeFailure.badRequest("TRIAL_UNCOVERED", DreamPhoneWorks.UNCOVERED);
        }
        ObjectNode row = CanonicalJson.MAPPER.createObjectNode();
        row.put("id", "ph_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8));
        row.put("url", url == null ? "" : url);
        row.put("desc", desc == null ? "" : desc);
        row.put("time", System.currentTimeMillis());
        session.withArray("photos").insert(0, row);
        session.put("screen", "pageGallery");
        persist(sessionId, session);
    }

    private void createChar(String sessionId, ObjectNode session, String name, String emoji, String color) {
        if (session.path("trial").asBoolean(false)) {
            throw RuntimeFailure.badRequest("TRIAL_UNCOVERED", DreamPhoneWorks.UNCOVERED);
        }
        String id = "c_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        ObjectNode row = CanonicalJson.MAPPER.createObjectNode();
        row.put("id", id);
        row.put("name", name);
        row.put("emoji", blank(emoji) ? "\uD83D\uDE0A" : emoji);
        row.put("color", blank(color) ? "#6D81A1" : color);
        row.put("avatar", "");
        session.withArray("characters").add(row);
        session.with("charWallets").put(id, DreamPhoneWorks.WALLET);
        session.with("chatHistory").set(id, CanonicalJson.MAPPER.createArrayNode());
        session.put("currentCharId", id);
        session.put("screen", "pageWechatMain");
        persist(sessionId, session);
    }

    private void deleteChar(String sessionId, ObjectNode session, String charId) {
        if (DreamPhoneWorks.CHARACTER_ID.equals(charId)) {
            throw RuntimeFailure.badRequest("INVALID_MUTATION", "default character cannot be deleted");
        }
        ArrayNode next = CanonicalJson.MAPPER.createArrayNode();
        for (JsonNode character : session.path("characters")) {
            if (!charId.equals(character.path("id").asText())) {
                next.add(character);
            }
        }
        session.set("characters", next);
        if (charId.equals(session.path("currentCharId").asText())) {
            session.put("currentCharId", DreamPhoneWorks.CHARACTER_ID);
        }
        persist(sessionId, session);
    }

    private void gameResult(String sessionId, ObjectNode session, String game, String result) {
        String charId = session.path("currentCharId").asText(DreamPhoneWorks.CHARACTER_ID);
        ObjectNode record = session.with("gameRecords").with(game);
        record.put("lastResult", result);
        record.put("plays", record.path("plays").asInt(0) + 1);
        pushChat(session, charId, "assistant", "game_result", game + "\uFF1A" + result);
        session.put("screen", "pageChat");
        persist(sessionId, session);
    }

    private void placeOrder(String sessionId, ObjectNode session, String app, String title, double amount) {
        if (session.path("trial").asBoolean(false)) {
            throw RuntimeFailure.badRequest("TRIAL_UNCOVERED", DreamPhoneWorks.UNCOVERED);
        }
        ObjectNode order = CanonicalJson.MAPPER.createObjectNode();
        order.put("id", "ord_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8));
        order.put("app", app);
        order.put("title", title);
        order.put("amount", amount);
        order.put("status", "delivering");
        order.put("time", System.currentTimeMillis());
        session.withArray("orders").insert(0, order);
        session.put("screen", "jingxi".equals(app) ? "pageJingxi" : "pageBaoleme");
        persist(sessionId, session);
    }

    private void callEnd(String sessionId, ObjectNode session, String note) {
        String charId = session.path("currentCharId").asText(DreamPhoneWorks.CHARACTER_ID);
        ObjectNode call = CanonicalJson.MAPPER.createObjectNode();
        call.put("note", blank(note) ? "\u901A\u8BDD\u7ED3\u675F" : note);
        call.put("time", System.currentTimeMillis());
        session.withArray("calls").add(call);
        pushChat(session, charId, "assistant", "text", blank(note) ? "\u521A\u624D\u7535\u8BDD\u633A\u597D\u7684" : note);
        session.put("screen", "pageChat");
        persist(sessionId, session);
    }

    private void callTool(long accountId, String sessionId, String tool, String args) {
        if (tools == null) {
            return;
        }
        ToolResult result = tools.execute(new ToolExecutionCommand(
                tool,
                "1",
                new SessionRef(sessionId, DreamPhoneWorks.CODE),
                accountId,
                DreamPhoneWorks.CODE,
                sessionId + ":" + tool + ":" + UUID.randomUUID().toString().replace("-", "").substring(0, 12),
                args));
        if (!result.success()) {
            throw RuntimeFailure.badRequest(result.code(), result.message());
        }
    }

    private static String contentArgs(String content) {
        ObjectNode args = CanonicalJson.MAPPER.createObjectNode();
        args.put("content", content);
        return CanonicalJson.write(args);
    }

    private static String aiPayload(ObjectNode session, String text, AccountAiSettingsPort.Snapshot saved) {
        JsonNode persona = session.path("persona");
        String prompt = "outer: " + persona.path("outer").asText()
                + "\ninner: " + persona.path("inner").asText()
                + "\norigin: " + persona.path("origin").asText()
                + "\nspeech: " + persona.path("speech").asText()
                + "\nredlines: " + persona.path("redlines").asText()
                + "\n\n" + text;
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("channel", "BYOK");
        body.put("text", prompt);
        body.put("content", text);
        body.put("baseUrl", saved.baseUrl());
        body.put("endpoint", saved.baseUrl());
        body.put("model", saved.model());
        body.put("apiKey", saved.apiKey());
        return CanonicalJson.write(CanonicalJson.MAPPER.valueToTree(body));
    }

    private static void pushChat(ObjectNode session, String charId, String role, String type, String content) {
        ObjectNode row = CanonicalJson.MAPPER.createObjectNode();
        row.put("role", role);
        row.put("type", type);
        row.put("content", content);
        row.put("time", System.currentTimeMillis());
        thread(session, charId).add(row);
    }

    private static ArrayNode thread(ObjectNode session, String charId) {
        ObjectNode history = session.with("chatHistory");
        if (!history.has(charId) || !history.get(charId).isArray()) {
            history.set(charId, CanonicalJson.MAPPER.createArrayNode());
        }
        return (ArrayNode) history.get(charId);
    }

    private ObjectNode loadOwned(long accountId, String sessionId) {
        SessionRef ref = new SessionRef(sessionId, DreamPhoneWorks.CODE);
        sessions.requireOwner(accountId, ref);
        SessionRecord row = store.findSession(sessionId).orElseThrow(RuntimeFailure::crossSession);
        JsonNode payload = CanonicalJson.parse(row.payloadJson());
        if (!(payload instanceof ObjectNode objectNode) || !DreamPhoneWorks.CODE.equals(objectNode.path("work").asText())) {
            throw RuntimeFailure.badRequest("SESSION_WORK_MISMATCH", "not a dream-phone session");
        }
        if (!objectNode.path("trial").asBoolean(false)) {
            entitlements.requireEntitlement(accountId, DreamPhoneWorks.CODE);
        }
        return objectNode;
    }

    private void persist(String sessionId, ObjectNode session) {
        SessionRecord row = store.findSession(sessionId).orElseThrow(RuntimeFailure::crossSession);
        sessions.apply(
                new SessionRef(sessionId, DreamPhoneWorks.CODE),
                new StateMutation("REPLACE", row.schemaVersion(), CanonicalJson.write(session)));
    }

    private static String string(Map<String, Object> body, String key) {
        Object value = body == null ? null : body.get(key);
        if (value == null || String.valueOf(value).isBlank()) {
            throw RuntimeFailure.badRequest("INVALID_MUTATION", key + " is required");
        }
        return String.valueOf(value);
    }

    private static String optional(Map<String, Object> body, String key) {
        Object value = body == null ? null : body.get(key);
        return value == null ? "" : String.valueOf(value);
    }

    private static double number(Map<String, Object> body, String key) {
        try {
            return Double.parseDouble(string(body, key));
        } catch (NumberFormatException ex) {
            throw RuntimeFailure.badRequest("INVALID_MUTATION", key + " is required");
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
