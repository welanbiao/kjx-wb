package com.koujunxin.tool.json;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.koujunxin.tool.api.ToolFailure;

public final class JsonSupport {

    private JsonSupport() {
    }

    public static JsonNode read(ObjectMapper mapper, String json) {
        if (json == null || json.isBlank()) {
            return mapper.createObjectNode();
        }
        try {
            return mapper.readTree(json);
        } catch (JsonProcessingException ex) {
            throw ToolFailure.of("INVALID_ARGUMENTS", "arguments are not valid JSON");
        }
    }

    public static String write(ObjectMapper mapper, JsonNode node) {
        try {
            return mapper.writeValueAsString(node == null ? mapper.createObjectNode() : node);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("json write failed", ex);
        }
    }

    public static String sha256(String canonical) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte value : digest) {
                hex.append(Character.forDigit((value >> 4) & 0xf, 16));
                hex.append(Character.forDigit(value & 0xf, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    public static String argumentsHash(String toolCode, String toolVersion, JsonNode arguments) {
        return sha256(toolCode + '\0' + toolVersion + '\0' + canonical(arguments));
    }

    public static String canonical(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return "{}";
        }
        return node.toString();
    }

    public static JsonNode get(JsonNode root, String path) {
        if (root == null) {
            return MissingNode.getInstance();
        }
        String[] parts = split(path);
        if (parts.length == 0) {
            return root;
        }
        JsonNode current = root;
        for (String part : parts) {
            if (current == null || current.isMissingNode() || current.isNull()) {
                return MissingNode.getInstance();
            }
            if (current.isArray() && part.chars().allMatch(Character::isDigit)) {
                int index = Integer.parseInt(part);
                current = index >= 0 && index < current.size() ? current.get(index) : MissingNode.getInstance();
            } else if (current.isObject()) {
                current = current.path(part);
            } else {
                return MissingNode.getInstance();
            }
        }
        return current == null ? MissingNode.getInstance() : current;
    }

    public static void set(ObjectMapper mapper, ObjectNode root, String path, JsonNode value) {
        String[] parts = split(path);
        if (parts.length == 0) {
            throw ToolFailure.of("COMPOSE_FAILED", "state path is required");
        }
        ObjectNode current = root;
        for (int i = 0; i < parts.length - 1; i++) {
            JsonNode child = current.get(parts[i]);
            if (child == null || !child.isObject()) {
                ObjectNode created = mapper.createObjectNode();
                current.set(parts[i], created);
                current = created;
            } else {
                current = (ObjectNode) child;
            }
        }
        current.set(parts[parts.length - 1], value == null ? mapper.nullNode() : value);
    }

    public static void merge(ObjectNode target, JsonNode delta) {
        if (delta == null || !delta.isObject()) {
            return;
        }
        for (Map.Entry<String, JsonNode> field : delta.properties()) {
            JsonNode incoming = field.getValue();
            JsonNode existing = target.get(field.getKey());
            if (existing != null && existing.isObject() && incoming != null && incoming.isObject()) {
                merge((ObjectNode) existing, incoming);
            } else {
                target.set(field.getKey(), incoming);
            }
        }
    }

    public static JsonNode interpolate(ObjectMapper mapper, JsonNode template, JsonNode scope) {
        if (template == null || template.isMissingNode() || template.isNull()) {
            return template;
        }
        if (template.isTextual()) {
            String text = template.asText();
            if (text.startsWith("$.")) {
                JsonNode resolved = get(scope, text);
                return resolved.isMissingNode() ? mapper.nullNode() : resolved;
            }
            return template;
        }
        if (template.isArray()) {
            ArrayNode copy = mapper.createArrayNode();
            for (JsonNode item : template) {
                copy.add(interpolate(mapper, item, scope));
            }
            return copy;
        }
        if (template.isObject()) {
            ObjectNode copy = mapper.createObjectNode();
            for (Map.Entry<String, JsonNode> field : template.properties()) {
                copy.set(field.getKey(), interpolate(mapper, field.getValue(), scope));
            }
            return copy;
        }
        return template;
    }

    public static boolean looksLikeScript(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        return lower.contains("function")
                || lower.contains("eval(")
                || lower.contains("=>")
                || lower.contains("javascript")
                || lower.contains("new ");
    }

    private static String[] split(String path) {
        if (path == null || path.isBlank() || "$".equals(path.trim())) {
            return new String[0];
        }
        String trimmed = path.trim();
        if (trimmed.startsWith("$.")) {
            trimmed = trimmed.substring(2);
        } else if (trimmed.startsWith("$")) {
            trimmed = trimmed.substring(1);
            if (trimmed.startsWith(".")) {
                trimmed = trimmed.substring(1);
            }
        }
        if (trimmed.isBlank()) {
            return new String[0];
        }
        return trimmed.split("\\.");
    }
}
