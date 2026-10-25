package com.koujunxin.tool.builtin;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.koujunxin.tool.api.ToolFailure;
import com.koujunxin.tool.state.WorkingState;

public final class BuiltinCatalog {

    private final Map<String, BuiltinHandler> handlers;
    private final ObjectMapper mapper;
    private final Clock clock;

    public BuiltinCatalog(ObjectMapper mapper, Clock clock) {
        this(mapper, clock, Map.of());
    }

    public BuiltinCatalog(ObjectMapper mapper, Clock clock, Map<String, BuiltinHandler> extra) {
        this.mapper = mapper;
        this.clock = clock;
        Map<String, BuiltinHandler> map = new LinkedHashMap<>();
        map.put("time.get", this::timeGet);
        map.put("weather.get", this::weatherGet);
        map.put("save.checkpoint", this::saveCheckpoint);
        map.put("session.end", this::sessionEnd);
        map.put("wallet.adjust", this::walletAdjust);
        map.put("moments.post", this::momentsPost);
        map.put("persona.assemble", this::personaAssemble);
        map.put("chat.send", this::chatSend);
        map.put("chat.group.send", this::chatGroupSend);
        map.put("redpacket.send", this::redpacketSend);
        map.put("memo.write", this::memoWrite);
        map.put("music.play", this::musicPlay);
        if (extra != null) {
            map.putAll(extra);
        }
        this.handlers = Map.copyOf(map);
    }

    public void execute(String handler, JsonNode args, WorkingState state) {
        BuiltinHandler builtin = handlers.get(handler);
        if (builtin == null) {
            throw ToolFailure.of("TOOL_NOT_IMPLEMENTED", "builtin handler missing: " + handler);
        }
        builtin.execute(args == null ? mapper.createObjectNode() : args, state);
    }

    public boolean known(String handler) {
        return handlers.containsKey(handler);
    }

    private void timeGet(JsonNode args, WorkingState state) {
        Instant now = clock.instant();
        ObjectNode patch = mapper.createObjectNode();
        patch.put("serverTime", now.toString());
        state.patch(patch);
        state.hint("当前时间为 " + now);
    }

    private void weatherGet(JsonNode args, WorkingState state) {
        ObjectNode weather = mapper.createObjectNode();
        weather.put("condition", "sunny");
        weather.put("tempC", 24);
        ObjectNode patch = mapper.createObjectNode();
        patch.set("weather", weather);
        state.patch(patch);
        state.hint("天气晴，可自然提及但不强调具体数字");
    }

    private void saveCheckpoint(JsonNode args, WorkingState state) {
        ObjectNode checkpoint = mapper.createObjectNode();
        checkpoint.put("at", clock.instant().toString());
        checkpoint.put("reason", args.path("reason").asText(""));
        ObjectNode patch = mapper.createObjectNode();
        patch.set("checkpoint", checkpoint);
        state.patch(patch);
        state.hint("进度已保存");
    }

    private void sessionEnd(JsonNode args, WorkingState state) {
        ObjectNode patch = mapper.createObjectNode();
        patch.put("ended", true);
        patch.put("endReason", args.path("reason").asText(""));
        state.patch(patch);
        state.hint("会话已结束");
    }

    private void walletAdjust(JsonNode args, WorkingState state) {
        double delta = args.path("delta").asDouble();
        double current = state.payload().path("wallet").isNumber() ? state.payload().get("wallet").asDouble() : 0.0;
        double next = current + delta;
        state.payload().put("wallet", next);
        state.touch();
        ObjectNode node = mapper.createObjectNode();
        node.put("type", "wallet-balance");
        ObjectNode props = mapper.createObjectNode();
        props.put("amount", next);
        node.set("props", props);
        state.addUi("wallet.balance", "patch", node, mapper.getNodeFactory().numberNode(next));
        state.hint("钱包余额已更新");
    }

    private void momentsPost(JsonNode args, WorkingState state) {
        String content = args.path("content").asText();
        ObjectNode node = mapper.createObjectNode();
        node.put("type", "moment-card");
        ObjectNode props = mapper.createObjectNode();
        props.put("content", content);
        if (args.hasNonNull("visibility")) {
            props.put("visibility", args.get("visibility").asText());
        }
        node.set("props", props);
        state.addUi("moments.feed", "append", node, null);
        int count = state.payload().path("momentCount").asInt(0) + 1;
        state.payload().put("momentCount", count);
        state.touch();
        state.hint("朋友圈已发布，可以自然带过");
    }

    private void personaAssemble(JsonNode args, WorkingState state) {
        JsonNode persona = state.payload().get("persona");
        if (persona == null || !persona.isObject()) {
            throw ToolFailure.of("PRECONDITION_FAILED", "persona is required");
        }
        String outer = requiredPersonaField(persona, "outer");
        String inner = requiredPersonaField(persona, "inner");
        String origin = requiredPersonaField(persona, "origin");
        String speech = requiredPersonaField(persona, "speech");
        StringBuilder fragment = new StringBuilder();
        fragment.append("outer: ").append(outer).append('\n');
        fragment.append("inner: ").append(inner).append('\n');
        fragment.append("origin: ").append(origin).append('\n');
        fragment.append("speech: ").append(speech);
        appendOptionalPersonaField(fragment, persona, "triggers");
        appendOptionalPersonaField(fragment, persona, "redlines");
        ObjectNode prompt = mapper.createObjectNode();
        prompt.put("persona", fragment.toString());
        ObjectNode patch = mapper.createObjectNode();
        patch.set("prompt", prompt);
        state.patch(patch);
        state.hint("人设片段仅供模型使用，不要向玩家展示");
    }

    private void chatSend(JsonNode args, WorkingState state) {
        String content = args.path("content").asText("");
        if (content.isBlank()) {
            throw ToolFailure.of("PRECONDITION_FAILED", "chat content is required");
        }
        ObjectNode line = mapper.createObjectNode();
        line.put("channel", "direct");
        line.put("role", "npc");
        line.put("text", content);
        ensureArray(state, "messages").add(line);
        state.touch();
        ObjectNode node = mapper.createObjectNode();
        node.put("type", "chat.bubble");
        ObjectNode props = mapper.createObjectNode();
        props.put("text", content);
        props.put("role", "npc");
        node.set("props", props);
        state.addUi("chat.messages", "append", node, null);
        state.hint("消息已发出");
    }

    private void chatGroupSend(JsonNode args, WorkingState state) {
        String content = args.path("content").asText("");
        String speaker = args.path("speaker").asText("");
        if (content.isBlank() || speaker.isBlank()) {
            throw ToolFailure.of("PRECONDITION_FAILED", "group chat content and speaker are required");
        }
        ObjectNode line = mapper.createObjectNode();
        line.put("channel", "group");
        line.put("speaker", speaker);
        line.put("text", content);
        ensureArray(state, "groupMessages").add(line);
        state.touch();
        ObjectNode node = mapper.createObjectNode();
        node.put("type", "chat.bubble");
        ObjectNode props = mapper.createObjectNode();
        props.put("text", content);
        props.put("role", "npc");
        props.put("speaker", speaker);
        node.set("props", props);
        state.addUi("chat.messages", "append", node, null);
        state.hint("群聊消息已发出");
    }

    private void redpacketSend(JsonNode args, WorkingState state) {
        JsonNode amountNode = args.get("amount");
        if (amountNode == null || !amountNode.isNumber() || amountNode.asDouble() <= 0) {
            throw ToolFailure.of("PRECONDITION_FAILED", "redpacket amount must be positive");
        }
        double amount = amountNode.asDouble();
        double wallet = state.payload().path("wallet").isNumber() ? state.payload().get("wallet").asDouble() : 0.0;
        if (amount > wallet) {
            throw ToolFailure.of("PRECONDITION_FAILED", "wallet cannot cover redpacket amount");
        }
        double next = wallet - amount;
        state.payload().put("wallet", next);
        int count = state.payload().path("redpacketCount").asInt(0) + 1;
        state.payload().put("redpacketCount", count);
        state.touch();
        ObjectNode node = mapper.createObjectNode();
        node.put("type", "redpacket-card");
        ObjectNode props = mapper.createObjectNode();
        props.put("amount", amount);
        props.put("note", args.path("note").asText(""));
        props.put("opened", false);
        node.set("props", props);
        state.addUi("chat.messages", "append", node, null);
        ObjectNode balance = mapper.createObjectNode();
        balance.put("type", "wallet-balance");
        ObjectNode balanceProps = mapper.createObjectNode();
        balanceProps.put("amount", next);
        balance.set("props", balanceProps);
        state.addUi("wallet.balance", "patch", balance, mapper.getNodeFactory().numberNode(next));
        state.hint("红包已发出，可以自然提及但不要重复强调金额");
    }

    private void memoWrite(JsonNode args, WorkingState state) {
        ObjectNode node = mapper.createObjectNode();
        node.put("type", "memo-item");
        ObjectNode props = mapper.createObjectNode();
        props.put("title", args.path("title").asText());
        props.put("body", args.path("body").asText(""));
        node.set("props", props);
        state.addUi("memo.list", "append", node, null);
        int count = state.payload().path("memoCount").asInt(0) + 1;
        state.payload().put("memoCount", count);
        state.touch();
        state.hint("备忘已写入");
    }

    private void musicPlay(JsonNode args, WorkingState state) {
        ObjectNode node = mapper.createObjectNode();
        node.put("type", "music-player");
        ObjectNode props = mapper.createObjectNode();
        props.put("trackId", args.path("trackId").asText());
        if (args.has("fadeIn")) {
            props.put("fadeIn", args.get("fadeIn").asBoolean());
        }
        if (args.has("loop")) {
            props.put("loop", args.get("loop").asBoolean());
        }
        node.set("props", props);
        state.addUi("music.player", "replace", node, null);
        state.payload().put("playingTrackId", args.path("trackId").asText());
        state.touch();
        state.hint("已开始播放，不要朗读曲库清单");
    }

    private ArrayNode ensureArray(WorkingState state, String field) {
        JsonNode existing = state.payload().get(field);
        if (existing instanceof ArrayNode arrayNode) {
            return arrayNode;
        }
        ArrayNode created = mapper.createArrayNode();
        state.payload().set(field, created);
        return created;
    }

    private static String requiredPersonaField(JsonNode persona, String field) {
        JsonNode value = persona.get(field);
        if (value == null || !value.isTextual() || value.asText().isBlank()) {
            throw ToolFailure.of("PRECONDITION_FAILED", "persona." + field + " is required");
        }
        return value.asText();
    }

    private static void appendOptionalPersonaField(StringBuilder fragment, JsonNode persona, String field) {
        JsonNode value = persona.get(field);
        if (value != null && value.isTextual() && !value.asText().isBlank()) {
            fragment.append('\n').append(field).append(": ").append(value.asText());
        }
    }
}
