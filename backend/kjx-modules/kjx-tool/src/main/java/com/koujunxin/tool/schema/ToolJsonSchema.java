package com.koujunxin.tool.schema;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 工具参数 / UI props 使用的 JSON Schema 子集：type、properties、required、enum、数值与长度约束。
 */
public final class ToolJsonSchema {

    public List<String> validate(JsonNode schema, JsonNode data) {
        List<String> errors = new ArrayList<>();
        if (schema == null || schema.isMissingNode() || schema.isNull()) {
            errors.add("schema is required");
            return errors;
        }
        validateNode(schema, data, "$", errors);
        return errors;
    }

    private void validateNode(JsonNode schema, JsonNode data, String path, List<String> errors) {
        if (schema.has("const") && (data == null || data.isMissingNode() || !schema.get("const").equals(data))) {
            errors.add(path + " must equal const");
            return;
        }
        if (schema.has("enum")) {
            boolean matched = false;
            for (JsonNode option : schema.get("enum")) {
                if (option.equals(data)) {
                    matched = true;
                    break;
                }
            }
            if (!matched) {
                errors.add(path + " is not in enum");
                return;
            }
        }
        if (schema.has("type")) {
            if (!typeMatches(schema.get("type").asText(), data)) {
                errors.add(path + " expected type " + schema.get("type").asText());
                return;
            }
        }
        if (data == null || data.isMissingNode() || data.isNull()) {
            return;
        }
        if (data.isTextual()) {
            int length = data.asText().length();
            if (schema.has("minLength") && length < schema.get("minLength").asInt()) {
                errors.add(path + " shorter than minLength");
            }
            if (schema.has("maxLength") && length > schema.get("maxLength").asInt()) {
                errors.add(path + " longer than maxLength");
            }
        }
        if (data.isNumber()) {
            double value = data.asDouble();
            if (schema.has("minimum") && value < schema.get("minimum").asDouble()) {
                errors.add(path + " below minimum");
            }
            if (schema.has("maximum") && value > schema.get("maximum").asDouble()) {
                errors.add(path + " above maximum");
            }
            if (schema.has("exclusiveMinimum") && value <= schema.get("exclusiveMinimum").asDouble()) {
                errors.add(path + " not above exclusiveMinimum");
            }
            if (schema.has("exclusiveMaximum") && value >= schema.get("exclusiveMaximum").asDouble()) {
                errors.add(path + " not below exclusiveMaximum");
            }
        }
        if (data.isArray()) {
            if (schema.has("minItems") && data.size() < schema.get("minItems").asInt()) {
                errors.add(path + " has too few items");
            }
            if (schema.has("maxItems") && data.size() > schema.get("maxItems").asInt()) {
                errors.add(path + " has too many items");
            }
            JsonNode items = schema.get("items");
            if (items != null && items.isObject()) {
                for (int i = 0; i < data.size(); i++) {
                    validateNode(items, data.get(i), path + "[" + i + "]", errors);
                }
            }
        }
        if (data.isObject()) {
            JsonNode required = schema.get("required");
            if (required != null && required.isArray()) {
                for (JsonNode name : required) {
                    if (!data.has(name.asText())) {
                        errors.add(path + "." + name.asText() + " is required");
                    }
                }
            }
            JsonNode properties = schema.get("properties");
            boolean allowAdditional = !schema.has("additionalProperties")
                    || schema.get("additionalProperties").asBoolean(true);
            for (Map.Entry<String, JsonNode> field : data.properties()) {
                JsonNode propertySchema = properties == null ? null : properties.get(field.getKey());
                if (propertySchema != null) {
                    validateNode(propertySchema, field.getValue(), path + "." + field.getKey(), errors);
                } else if (!allowAdditional) {
                    errors.add(path + "." + field.getKey() + " is not allowed");
                }
            }
        }
    }

    private static boolean typeMatches(String type, JsonNode data) {
        String expected = type == null ? "" : type.toLowerCase(Locale.ROOT);
        if (data == null || data.isMissingNode() || data.isNull()) {
            return "null".equals(expected);
        }
        return switch (expected) {
            case "object" -> data.isObject();
            case "array" -> data.isArray();
            case "string" -> data.isTextual();
            case "boolean" -> data.isBoolean();
            case "integer" -> data.isIntegralNumber();
            case "number" -> data.isNumber();
            case "null" -> data.isNull();
            default -> true;
        };
    }
}
