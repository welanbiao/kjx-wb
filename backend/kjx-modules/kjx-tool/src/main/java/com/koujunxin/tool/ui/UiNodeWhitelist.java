package com.koujunxin.tool.ui;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.koujunxin.tool.api.ToolFailure;
import com.koujunxin.tool.schema.ToolJsonSchema;

/**
 * 平台 UI 节点白名单。非白名单 type 拒绝；props 走 Schema；禁止 html/script/style。
 */
public final class UiNodeWhitelist {

    private static final Pattern CUSTOM_SLOT = Pattern.compile("^custom\\.slot:[a-z0-9][a-z0-9_-]{0,31}$");

    private final Map<String, JsonNode> schemas;
    private final ToolJsonSchema jsonSchema;
    private final ObjectMapper mapper;

    public UiNodeWhitelist(Map<String, JsonNode> schemas, ToolJsonSchema jsonSchema, ObjectMapper mapper) {
        this.schemas = Map.copyOf(schemas);
        this.jsonSchema = jsonSchema;
        this.mapper = mapper;
    }

    public static UiNodeWhitelist of(Map<String, JsonNode> schemas, ObjectMapper mapper) {
        return new UiNodeWhitelist(schemas, new ToolJsonSchema(), mapper);
    }

    public void validateInstructions(JsonNode ui) {
        if (ui == null || ui.isNull() || ui.isMissingNode()) {
            return;
        }
        if (!ui.isArray()) {
            throw ToolFailure.of("UI_PROPS_INVALID", "ui must be an array");
        }
        for (JsonNode item : ui) {
            JsonNode node = item.get("node");
            if (node != null && !node.isNull() && !node.isMissingNode()) {
                validateNode(node);
            }
        }
    }

    public void validateNode(JsonNode node) {
        if (node == null || !node.isObject()) {
            throw ToolFailure.of("UI_PROPS_INVALID", "node must be an object");
        }
        String type = node.path("type").asText("");
        JsonNode props = node.get("props");
        if (props == null || props.isMissingNode() || props.isNull()) {
            props = mapper.createObjectNode();
        }
        rejectRawMarkup(props);
        if (type.startsWith("custom.slot:")) {
            if (!CUSTOM_SLOT.matcher(type).matches()) {
                throw ToolFailure.of("UI_NODE_NOT_ALLOWED", "node.type not allowed: " + type);
            }
            if (!props.isObject()) {
                throw ToolFailure.of("UI_PROPS_INVALID", "custom slot props must be an object");
            }
            return;
        }
        JsonNode schema = schemas.get(type);
        if (schema == null) {
            throw ToolFailure.of("UI_NODE_NOT_ALLOWED", "node.type not allowed: " + type);
        }
        List<String> errors = jsonSchema.validate(schema, props);
        if (!errors.isEmpty()) {
            throw ToolFailure.of("UI_PROPS_INVALID", String.join("; ", errors));
        }
    }

    public Map<String, JsonNode> schemas() {
        return schemas;
    }

    static void rejectRawMarkup(JsonNode props) {
        if (props == null || !props.isObject()) {
            return;
        }
        Iterator<String> names = ((ObjectNode) props).fieldNames();
        while (names.hasNext()) {
            String name = names.next().toLowerCase(Locale.ROOT);
            if ("html".equals(name) || "script".equals(name) || "style".equals(name)) {
                throw ToolFailure.of("UI_PROPS_INVALID", "forbidden field: " + name);
            }
        }
    }

    public static Map<String, JsonNode> copyOf(Map<String, JsonNode> source) {
        return new LinkedHashMap<>(source);
    }
}
