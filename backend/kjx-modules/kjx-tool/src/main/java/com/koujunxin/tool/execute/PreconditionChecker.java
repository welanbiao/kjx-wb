package com.koujunxin.tool.execute;

import java.util.List;
import java.util.Locale;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.koujunxin.runtime.api.dto.SessionStateSnapshot;
import com.koujunxin.tool.api.ToolFailure;
import com.koujunxin.tool.api.ToolSpec;
import com.koujunxin.tool.json.JsonSupport;
import com.koujunxin.tool.schema.ToolJsonSchema;

/**
 * 剧情/阶段前置条件。不读取会员 VALID/EXPIRED。
 */
public final class PreconditionChecker {

    private final ObjectMapper mapper;

    public PreconditionChecker(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public void check(ToolSpec spec, SessionStateSnapshot snapshot, JsonNode args) {
        JsonNode pre = spec.preconditions();
        if (pre == null || pre.isMissingNode() || pre.isNull() || pre.isEmpty()) {
            return;
        }
        if (pre.isArray()) {
            for (JsonNode item : pre) {
                checkExpression(item.asText(), snapshot);
            }
            return;
        }
        if (pre.has("stageIn")) {
            boolean allowed = false;
            for (JsonNode stage : pre.get("stageIn")) {
                if (snapshot.stage().equals(stage.asText())) {
                    allowed = true;
                    break;
                }
            }
            if (!allowed) {
                throw ToolFailure.of("PRECONDITION_FAILED", "session stage not allowed");
            }
        }
        if (pre.has("stageMin")) {
            int current = parseStage(snapshot);
            if (current < pre.get("stageMin").asInt()) {
                throw ToolFailure.of("PRECONDITION_FAILED", "session stage too early");
            }
        }
        if (pre.has("payloadEquals")) {
            JsonNode payload = JsonSupport.read(mapper, snapshot.payloadJson());
            JsonNode expected = pre.get("payloadEquals");
            expected.properties().forEach(entry -> {
                JsonNode actual = payload.path(entry.getKey());
                if (!actual.equals(entry.getValue())) {
                    throw ToolFailure.of("PRECONDITION_FAILED", "payload mismatch: " + entry.getKey());
                }
            });
        }
    }

    private static void checkExpression(String expr, SessionStateSnapshot snapshot) {
        if (expr == null || expr.isBlank()) {
            return;
        }
        if (JsonSupport.looksLikeScript(expr)) {
            throw ToolFailure.of("TOOL_SCRIPT_FORBIDDEN", "script is not supported");
        }
        String trimmed = expr.trim();
        if ("member.logged_in".equals(trimmed) || "work.unlocked".equals(trimmed)) {
            return;
        }
        if (trimmed.startsWith("session.stage") && trimmed.contains("==")) {
            String expected = unquote(trimmed.substring(trimmed.indexOf("==") + 2).trim());
            if (!snapshot.stage().equals(expected)) {
                throw ToolFailure.of("PRECONDITION_FAILED", "session stage not allowed");
            }
            return;
        }
        throw ToolFailure.of("PRECONDITION_FAILED", "unsupported precondition");
    }

    private int parseStage(SessionStateSnapshot snapshot) {
        String stage = snapshot.stage();
        try {
            return Integer.parseInt(stage);
        } catch (NumberFormatException ex) {
            JsonNode payload = JsonSupport.read(mapper, snapshot.payloadJson());
            if (payload.path("stageNo").isNumber()) {
                return payload.get("stageNo").asInt();
            }
            return Integer.MIN_VALUE;
        }
    }

    private static String unquote(String raw) {
        if ((raw.startsWith("'") && raw.endsWith("'") && raw.length() >= 2)
                || (raw.startsWith("\"") && raw.endsWith("\"") && raw.length() >= 2)) {
            return raw.substring(1, raw.length() - 1);
        }
        return raw;
    }

    public static void validateArguments(ToolJsonSchema schemas, ToolSpec spec, JsonNode args) {
        List<String> errors = schemas.validate(spec.parametersSchema(), args);
        if (!errors.isEmpty()) {
            throw ToolFailure.of("INVALID_ARGUMENTS", String.join("; ", errors));
        }
    }

    public static void rejectScript(ToolSpec spec) {
        if (spec.scriptKind()) {
            throw ToolFailure.of("TOOL_SCRIPT_FORBIDDEN", "script is not supported");
        }
        String kind = spec.kind().toLowerCase(Locale.ROOT);
        if (!"builtin".equals(kind) && !"compose".equals(kind)) {
            throw ToolFailure.of("TOOL_SCRIPT_FORBIDDEN", "implementation.kind must be builtin or compose");
        }
    }
}
