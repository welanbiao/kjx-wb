package com.koujunxin.ai.degrade;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class JsonExtractor {

    private final ObjectMapper mapper;

    public JsonExtractor() {
        this(new ObjectMapper());
    }

    public JsonExtractor(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public String extractObject(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.startsWith("```")) {
            int start = trimmed.indexOf('{');
            int fence = trimmed.lastIndexOf("```");
            if (start >= 0 && fence > start) {
                trimmed = trimmed.substring(start, fence).trim();
            } else if (start >= 0) {
                trimmed = trimmed.substring(start).trim();
            }
        }
        int from = trimmed.indexOf('{');
        int to = trimmed.lastIndexOf('}');
        if (from >= 0 && to > from) {
            trimmed = trimmed.substring(from, to + 1);
        }
        try {
            JsonNode node = mapper.readTree(trimmed);
            if (node != null && node.isObject()) {
                return mapper.writeValueAsString(node);
            }
        } catch (JsonProcessingException ignored) {
            return null;
        }
        return null;
    }

    public boolean matchesRequired(String json, String schemaJson) {
        if (json == null || json.isBlank()) {
            return false;
        }
        JsonNode object;
        try {
            object = mapper.readTree(json);
        } catch (JsonProcessingException | RuntimeException ex) {
            return false;
        }
        if (object == null || !object.isObject()) {
            return false;
        }
        if (schemaJson == null || schemaJson.isBlank()) {
            return true;
        }
        try {
            JsonNode schema = mapper.readTree(schemaJson);
            JsonNode required = schema.get("required");
            if (required != null && required.isArray()) {
                for (JsonNode name : required) {
                    if (!object.has(name.asText())) {
                        return false;
                    }
                }
            }
            return true;
        } catch (JsonProcessingException ex) {
            return false;
        }
    }

    public String toReadable(String raw) {
        String json = extractObject(raw);
        if (json == null) {
            if (raw == null || raw.isBlank()) {
                return "这次没有生成完整结果，请再试一次。";
            }
            return raw.trim();
        }
        try {
            JsonNode node = mapper.readTree(json);
            StringBuilder readable = new StringBuilder();
            node.fields().forEachRemaining(entry -> {
                if (readable.length() > 0) {
                    readable.append('\n');
                }
                readable.append(entry.getKey()).append('：').append(flatten(entry.getValue()));
            });
            return readable.isEmpty() ? json : readable.toString();
        } catch (JsonProcessingException ex) {
            return json;
        }
    }

    private static String flatten(JsonNode node) {
        if (node == null || node.isNull()) {
            return "";
        }
        if (node.isValueNode()) {
            return node.asText();
        }
        return node.toString();
    }
}
