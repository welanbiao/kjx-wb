package com.koujunxin.tool.compose;

import java.time.Clock;
import java.util.Locale;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.koujunxin.tool.api.ToolFailure;
import com.koujunxin.tool.builtin.BuiltinCatalog;
import com.koujunxin.tool.json.JsonSupport;
import com.koujunxin.tool.state.WorkingState;
import com.koujunxin.tool.ui.UiNodeWhitelist;

/**
 * 声明式 steps[] 编排。不执行脚本，不为每一步开启事务。
 */
public final class ComposeEngine {

    static final int MAX_STEPS = 64;
    static final int MAX_FOREACH = 32;
    static final int MAX_DEPTH = 8;

    private final ObjectMapper mapper;
    private final BuiltinCatalog builtins;
    private final UiNodeWhitelist ui;
    private final Clock clock;

    public ComposeEngine(ObjectMapper mapper, BuiltinCatalog builtins, UiNodeWhitelist ui, Clock clock) {
        this.mapper = mapper;
        this.builtins = builtins;
        this.ui = ui;
        this.clock = clock;
    }

    public void run(JsonNode implementation, JsonNode args, WorkingState state, int depth) {
        if (depth > MAX_DEPTH) {
            throw ToolFailure.of("COMPOSE_FAILED", "compose depth exceeded");
        }
        JsonNode steps = implementation.path("steps");
        if (!steps.isArray()) {
            throw ToolFailure.of("COMPOSE_FAILED", "compose steps must be an array");
        }
        ObjectNode vars = mapper.createObjectNode();
        runSteps(steps, args, vars, state, depth, 0);
    }

    private void runSteps(JsonNode steps, JsonNode args, ObjectNode vars, WorkingState state, int depth, int spent) {
        int count = spent;
        for (JsonNode step : steps) {
            if (state.returned()) {
                return;
            }
            count++;
            if (count > MAX_STEPS) {
                throw ToolFailure.of("COMPOSE_FAILED", "compose step limit exceeded");
            }
            runStep(step, args, vars, state, depth, count);
        }
    }

    private void runStep(JsonNode step, JsonNode args, ObjectNode vars, WorkingState state, int depth, int spent) {
        if (step == null || !step.isObject()) {
            throw ToolFailure.of("COMPOSE_FAILED", "compose step must be an object");
        }
        String op = step.path("op").asText("");
        if (op.isBlank()) {
            throw ToolFailure.of("COMPOSE_FAILED", "compose step op is required");
        }
        if (JsonSupport.looksLikeScript(op)) {
            throw ToolFailure.of("TOOL_SCRIPT_FORBIDDEN", "script is not supported");
        }
        ObjectNode scope = scope(args, vars, state);
        JsonNode value = interpolateValue(step.get("value"), scope);
        JsonNode argsNode = interpolateValue(step.get("args"), scope);
        JsonNode node = interpolateValue(step.get("node"), scope);
        switch (op) {
            case "state.get" -> bind(vars, step, JsonSupport.get(state.payload(), step.path("path").asText()));
            case "state.set" -> state.set(step.path("path").asText(), value);
            case "state.patch" -> state.patch(value);
            case "store.get" -> bind(vars, step, JsonSupport.get(state.store(), step.path("key").asText("key")));
            case "store.set" -> {
                JsonSupport.set(mapper, state.store(), step.path("key").asText("key"), value);
                state.touch();
            }
            case "store.incr" -> {
                String key = step.path("key").asText("key");
                int delta = step.path("delta").asInt(1);
                int current = JsonSupport.get(state.store(), key).asInt(0);
                JsonSupport.set(mapper, state.store(), key, mapper.getNodeFactory().numberNode(current + delta));
                state.touch();
            }
            case "ui.render" -> render(step, node, value, state);
            case "ui.toast" -> toast(step, state);
            case "ui.dialog" -> dialog(step, state);
            case "return" -> state.markReturned(value);
            case "if" -> {
                if (matches(step.get("cond"), scope)) {
                    runNested(step.get("then"), args, vars, state, depth, spent);
                } else {
                    runNested(step.get("else"), args, vars, state, depth, spent);
                }
            }
            case "switch" -> {
                String on = JsonSupport.get(scope, step.path("on").asText()).asText("");
                JsonNode cases = step.path("cases");
                JsonNode chosen = cases.has(on) ? cases.get(on) : step.get("default");
                runNested(chosen, args, vars, state, depth, spent);
            }
            case "assert" -> {
                if (!matches(step.get("cond"), scope)) {
                    throw ToolFailure.of("PRECONDITION_FAILED", step.path("message").asText("assertion failed"));
                }
            }
            case "foreach" -> {
                JsonNode list = interpolateValue(step.get("in"), scope);
                if (list != null && list.isTextual() && list.asText().startsWith("$.")) {
                    list = JsonSupport.get(scope, list.asText());
                }
                if (list == null || !list.isArray()) {
                    throw ToolFailure.of("COMPOSE_FAILED", "foreach in must be an array");
                }
                if (list.size() > MAX_FOREACH) {
                    throw ToolFailure.of("COMPOSE_FAILED", "foreach limit exceeded");
                }
                String as = step.path("as").asText("item");
                for (JsonNode item : list) {
                    vars.set(as, item);
                    runNested(step.get("do"), args, vars, state, depth, spent);
                    if (state.returned()) {
                        return;
                    }
                }
            }
            case "retry" -> {
                int times = Math.max(0, step.path("times").asInt(0));
                ToolFailure last = null;
                for (int i = 0; i <= times; i++) {
                    try {
                        runNested(step.get("steps"), args, vars, state, depth, spent);
                        last = null;
                        break;
                    } catch (ToolFailure ex) {
                        if (!ex.retryable() || i == times) {
                            throw ex;
                        }
                        last = ex;
                    }
                }
                if (last != null) {
                    throw last;
                }
            }
            case "timeout" -> {
                long budget = step.path("ms").asLong(0);
                long start = clock.millis();
                runNested(step.get("steps"), args, vars, state, depth, spent);
                if (budget > 0 && clock.millis() - start > budget) {
                    throw ToolFailure.of("TOOL_TIMEOUT", "信息不可用");
                }
            }
            case "transaction" -> runNested(step.get("steps"), args, vars, state, depth, spent);
            case "tool.call" -> {
                String name = step.path("name").asText();
                if (!builtins.known(name)) {
                    throw ToolFailure.of("TOOL_NOT_IMPLEMENTED", "subtool is not a builtin: " + name);
                }
                builtins.execute(name, argsNode, state);
            }
            default -> throw ToolFailure.of("TOOL_SCRIPT_FORBIDDEN", "unsupported compose op: " + op);
        }
    }

    private JsonNode interpolateValue(JsonNode value, JsonNode scope) {
        return JsonSupport.interpolate(mapper, value, scope);
    }

    private void runNested(JsonNode steps, JsonNode args, ObjectNode vars, WorkingState state, int depth, int spent) {
        if (steps == null || steps.isNull() || steps.isMissingNode()) {
            return;
        }
        if (!steps.isArray()) {
            throw ToolFailure.of("COMPOSE_FAILED", "nested steps must be an array");
        }
        runSteps(steps, args, vars, state, depth + 1, spent);
    }

    private void render(JsonNode step, JsonNode node, JsonNode value, WorkingState state) {
        String target = step.path("target").asText("chat.messages");
        String uiOp = step.path("uiOp").asText(step.path("patchOp").asText("append"));
        if (node != null && node.isObject()) {
            ui.validateNode(node);
        }
        state.addUi(target, uiOp, node, value);
    }

    private void toast(JsonNode step, WorkingState state) {
        ObjectNode node = mapper.createObjectNode();
        node.put("type", "toast");
        ObjectNode props = mapper.createObjectNode();
        props.put("text", step.path("text").asText(step.path("value").asText("")));
        node.set("props", props);
        ui.validateNode(node);
        state.addUi("toast", "append", node, null);
    }

    private void dialog(JsonNode step, WorkingState state) {
        ObjectNode node = mapper.createObjectNode();
        node.put("type", "dialog");
        ObjectNode props = mapper.createObjectNode();
        props.put("title", step.path("title").asText(""));
        props.put("text", step.path("text").asText(""));
        node.set("props", props);
        ui.validateNode(node);
        state.addUi("dialog", "append", node, null);
    }

    private void bind(ObjectNode vars, JsonNode step, JsonNode value) {
        String as = step.path("as").asText("");
        if (!as.isBlank()) {
            vars.set(as, value == null ? mapper.nullNode() : value);
        }
    }

    private ObjectNode scope(JsonNode args, ObjectNode vars, WorkingState state) {
        ObjectNode scope = mapper.createObjectNode();
        scope.set("args", args == null ? mapper.createObjectNode() : args);
        scope.set("vars", vars);
        scope.set("state", state.payload());
        return scope;
    }

    static boolean matches(JsonNode cond, JsonNode scope) {
        if (cond == null || cond.isMissingNode() || cond.isNull()) {
            return false;
        }
        if (cond.isBoolean()) {
            return cond.asBoolean();
        }
        if (cond.isTextual()) {
            return matchText(cond.asText(), scope);
        }
        if (!cond.isObject()) {
            return false;
        }
        String path = cond.path("path").asText();
        JsonNode actual = JsonSupport.get(scope, path);
        if (cond.has("exists")) {
            boolean exists = !actual.isMissingNode() && !actual.isNull();
            if (exists != cond.get("exists").asBoolean()) {
                return false;
            }
        }
        if (cond.has("eq") && !equalsValue(actual, cond.get("eq"))) {
            return false;
        }
        if (cond.has("neq") && equalsValue(actual, cond.get("neq"))) {
            return false;
        }
        if (cond.has("lte") && !(actual.isNumber() && actual.asDouble() <= cond.get("lte").asDouble())) {
            return false;
        }
        if (cond.has("gte") && !(actual.isNumber() && actual.asDouble() >= cond.get("gte").asDouble())) {
            return false;
        }
        if (cond.has("lt") && !(actual.isNumber() && actual.asDouble() < cond.get("lt").asDouble())) {
            return false;
        }
        if (cond.has("gt") && !(actual.isNumber() && actual.asDouble() > cond.get("gt").asDouble())) {
            return false;
        }
        return cond.has("path");
    }

    private static boolean matchText(String expr, JsonNode scope) {
        if (JsonSupport.looksLikeScript(expr)) {
            throw ToolFailure.of("TOOL_SCRIPT_FORBIDDEN", "script is not supported");
        }
        String trimmed = expr.trim();
        String[] parts = trimmed.split("==", 2);
        if (parts.length != 2) {
            JsonNode value = JsonSupport.get(scope, trimmed);
            return value.isBoolean() && value.asBoolean();
        }
        JsonNode left = JsonSupport.get(scope, parts[0].trim());
        String raw = parts[1].trim();
        if (raw.startsWith("'") && raw.endsWith("'") && raw.length() >= 2) {
            return left.asText().equals(raw.substring(1, raw.length() - 1));
        }
        if ("true".equals(raw.toLowerCase(Locale.ROOT))) {
            return left.asBoolean(false);
        }
        if ("false".equals(raw.toLowerCase(Locale.ROOT))) {
            return !left.asBoolean(true) && (left.isBoolean() || left.isMissingNode());
        }
        if (raw.chars().allMatch(ch -> Character.isDigit(ch) || ch == '-' || ch == '.')) {
            return left.isNumber() && Double.parseDouble(raw) == left.asDouble();
        }
        return left.asText().equals(raw);
    }

    private static boolean equalsValue(JsonNode actual, JsonNode expected) {
        if (expected == null) {
            return actual == null || actual.isNull() || actual.isMissingNode();
        }
        if (actual == null || actual.isMissingNode()) {
            return expected.isNull();
        }
        if (actual.isNumber() && expected.isNumber()) {
            return actual.asDouble() == expected.asDouble();
        }
        return actual.equals(expected);
    }
}
