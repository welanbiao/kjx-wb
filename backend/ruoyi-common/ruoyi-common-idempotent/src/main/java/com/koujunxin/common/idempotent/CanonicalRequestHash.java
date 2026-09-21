package com.koujunxin.common.idempotent;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * 稳定 canonical request hash：对象键排序、紧凑 JSON、UTF-8 SHA-256 小写十六进制。
 * 数组顺序保留。相同语义不同键序必须得到同一哈希。
 */
public final class CanonicalRequestHash {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .setNodeFactory(JsonNodeFactory.instance);

    private CanonicalRequestHash() {
    }

    public static String sha256Hex(Object payload) {
        try {
            JsonNode node = payload instanceof JsonNode json
                    ? json
                    : MAPPER.valueToTree(payload == null ? Map.of() : payload);
            String canonical = MAPPER.writeValueAsString(canonicalize(node));
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(canonical.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (JsonProcessingException | NoSuchAlgorithmException ex) {
            throw new IllegalStateException("canonical request hash failed", ex);
        }
    }

    public static String sha256HexJson(String json) {
        try {
            JsonNode node = (json == null || json.isBlank())
                    ? MAPPER.createObjectNode()
                    : MAPPER.readTree(json);
            return sha256Hex(node);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("request json is not valid", ex);
        }
    }

    static JsonNode canonicalize(JsonNode node) {
        if (node == null || node.isNull()) {
            return JsonNodeFactory.instance.nullNode();
        }
        if (node.isObject()) {
            Map<String, JsonNode> sorted = new TreeMap<>();
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                sorted.put(entry.getKey(), canonicalize(entry.getValue()));
            }
            ObjectNode object = JsonNodeFactory.instance.objectNode();
            sorted.forEach(object::set);
            return object;
        }
        if (node.isArray()) {
            ArrayNode array = JsonNodeFactory.instance.arrayNode();
            node.forEach(child -> array.add(canonicalize(child)));
            return array;
        }
        return node;
    }
}
