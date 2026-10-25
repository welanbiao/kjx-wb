package com.koujunxin.ai.prompt;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.koujunxin.ai.openai.OpenAiMessage;
import com.koujunxin.ai.session.ChatMessage;
import com.koujunxin.ai.session.ConversationSummary;

/**
 * 稳定前缀独立成第一条 system，可变后缀为第二条，以便 Prompt 缓存命中前缀。
 * breakup-editor 只拼人设片段与当前局面，不在此实现聊天或红包。
 */
public final class PromptAssembler {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String BREAKUP = "breakup-editor";

    public AssembledPrompt assemble(
            PromptRecord workPrompt,
            String runtimeStateJson,
            ConversationSummary summary,
            List<ChatMessage> recent,
            String jsonConstraint
    ) {
        if (workPrompt == null || workPrompt.kind() != PromptKind.WORK) {
            throw new IllegalArgumentException("runtime assembly requires WORK prompt");
        }
        String stable = workPrompt.stablePrefix();
        if (workPrompt.toolSpecJson() != null && !workPrompt.toolSpecJson().isBlank() && !"[]".equals(workPrompt.toolSpecJson())) {
            if (!stable.contains(workPrompt.toolSpecJson())) {
                stable = stable + "\n\n[tools]\n" + workPrompt.toolSpecJson();
            }
        }
        StringBuilder suffix = new StringBuilder();
        JsonNode state = parse(runtimeStateJson);
        String persona = personaFragment(state);
        if (!persona.isBlank()) {
            suffix.append("[persona]\n").append(persona).append('\n');
        }
        suffix.append("[state]\n").append(stateForPrompt(workPrompt.workCode(), state, runtimeStateJson)).append('\n');
        suffix.append("[summary]\n").append(summary == null ? "" : nullToEmpty(summary.summaryText())).append('\n');
        if (jsonConstraint != null && !jsonConstraint.isBlank()) {
            suffix.append("[output]\n").append(jsonConstraint).append('\n');
        }
        List<OpenAiMessage> history = new ArrayList<>();
        if (recent != null) {
            for (ChatMessage message : recent) {
                if (message.isSystem()) {
                    continue;
                }
                history.add(message.toOpenAi());
            }
        }
        return new AssembledPrompt(workPrompt.version(), stable, suffix.toString(), List.copyOf(history));
    }

    private static String personaFragment(JsonNode state) {
        if (state == null || state.isMissingNode()) {
            return "";
        }
        String assembled = state.path("prompt").path("persona").asText("");
        if (!assembled.isBlank()) {
            return assembled;
        }
        JsonNode persona = state.path("persona");
        if (!persona.isObject()) {
            return "";
        }
        String outer = persona.path("outer").asText("");
        String inner = persona.path("inner").asText("");
        String origin = persona.path("origin").asText("");
        String speech = persona.path("speech").asText("");
        if (outer.isBlank() || inner.isBlank() || origin.isBlank() || speech.isBlank()) {
            return "";
        }
        StringBuilder fragment = new StringBuilder();
        fragment.append("outer: ").append(outer).append('\n');
        fragment.append("inner: ").append(inner).append('\n');
        fragment.append("origin: ").append(origin).append('\n');
        fragment.append("speech: ").append(speech);
        return fragment.toString();
    }

    private static String stateForPrompt(String workCode, JsonNode state, String fallback) {
        if (!BREAKUP.equals(workCode) || state == null || !state.isObject() || !state.path("rewritten").isObject()) {
            return stripSecrets(blankToEmptyObject(fallback));
        }
        ObjectNode compact = MAPPER.createObjectNode();
        compact.put("coupleLabel", state.path("coupleLabel").asText(""));
        compact.put("side", state.path("side").asText(""));
        compact.put("worldline", state.path("worldline").asText(""));
        compact.put("frontier", state.path("frontier").asText(""));
        compact.put("bp", state.path("bp").asInt());
        compact.put("lm", state.path("lm").asInt());
        compact.put("lf", state.path("lf").asInt());
        compact.set("world", state.path("world").deepCopy());
        compact.set("titles", state.path("titles").deepCopy());
        compact.set("edits", state.path("edits").deepCopy());
        compact.set("rewritten", state.path("rewritten").deepCopy());
        compact.put("rule", "Do not recite exact BP/LM/LF numbers. Do not change title or bridgeNarr.");
        return compact.toString();
    }

    private static String stripSecrets(String json) {
        JsonNode node = parse(json);
        if (!node.isObject()) {
            return json;
        }
        stripSecretFields((ObjectNode) node);
        return node.toString();
    }

    private static void stripSecretFields(ObjectNode node) {
        Iterator<String> names = node.fieldNames();
        List<String> remove = new ArrayList<>();
        while (names.hasNext()) {
            String name = names.next();
            String lower = name.toLowerCase().replace("-", "").replace("_", "");
            if (lower.contains("apikey") || lower.equals("secret") || lower.equals("token") || lower.equals("authorization")) {
                remove.add(name);
                continue;
            }
            JsonNode child = node.get(name);
            if (child instanceof ObjectNode objectNode) {
                stripSecretFields(objectNode);
            }
        }
        for (String name : remove) {
            node.remove(name);
        }
    }

    private static JsonNode parse(String json) {
        try {
            if (json == null || json.isBlank()) {
                return MAPPER.createObjectNode();
            }
            return MAPPER.readTree(json);
        } catch (Exception ex) {
            return MAPPER.createObjectNode();
        }
    }

    private static String blankToEmptyObject(String json) {
        if (json == null || json.isBlank()) {
            return "{}";
        }
        return json;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
