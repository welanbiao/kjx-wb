package com.koujunxin.runtime.ledger;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.koujunxin.runtime.api.RuntimeFailure;

/**
 * 存档/状态校验用的规范 JSON 与 SHA-256。
 */
public final class CanonicalJson {

    public static final ObjectMapper MAPPER = new ObjectMapper();
    public static final String SCHEMA_V1 = "1";
    public static final int MAX_JSON_CHARS = 256_000;

    private CanonicalJson() {
    }

    public static void requireSchema(String schemaVersion) {
        if (!SCHEMA_V1.equals(schemaVersion)) {
            throw RuntimeFailure.schema("unsupported schemaVersion: " + schemaVersion);
        }
    }

    public static JsonNode parse(String json) {
        if (json == null || json.isBlank()) {
            return MAPPER.createObjectNode();
        }
        if (json.length() > MAX_JSON_CHARS) {
            throw RuntimeFailure.badRequest("PAYLOAD_TOO_LARGE", "json exceeds limit");
        }
        try {
            return MAPPER.readTree(json);
        } catch (Exception ex) {
            throw RuntimeFailure.badRequest("INVALID_JSON", "json is not valid");
        }
    }

    public static String canonicalize(String json) {
        return write(sort(parse(json)));
    }

    public static String write(JsonNode node) {
        try {
            return MAPPER.writeValueAsString(node == null ? MAPPER.createObjectNode() : node);
        } catch (Exception ex) {
            throw new IllegalStateException("json write failed", ex);
        }
    }

    public static JsonNode sort(JsonNode node) {
        if (node == null || node.isNull()) {
            return NullNode.getInstance();
        }
        if (node.isObject()) {
            ObjectNode out = MAPPER.createObjectNode();
            List<String> names = new ArrayList<>();
            Iterator<String> fields = node.fieldNames();
            while (fields.hasNext()) {
                names.add(fields.next());
            }
            Collections.sort(names);
            for (String name : names) {
                out.set(name, sort(node.get(name)));
            }
            return out;
        }
        if (node.isArray()) {
            ArrayNode out = MAPPER.createArrayNode();
            for (JsonNode child : node) {
                out.add(sort(child));
            }
            return out;
        }
        return node;
    }

    public static String checksum(String schemaVersion, String contentVersion, String stage, String payloadJson) {
        String canonical = canonicalize(payloadJson);
        return sha256(schemaVersion + "\n" + contentVersion + "\n" + stage + "\n" + canonical);
    }

    public static void verifyChecksum(
            String schemaVersion, String contentVersion, String stage, String payloadJson, String expected) {
        String actual = checksum(schemaVersion, contentVersion, stage, payloadJson);
        if (expected == null || !constantTimeEquals(actual, expected)) {
            throw RuntimeFailure.checksum("checksum mismatch");
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

    public static boolean constantTimeEquals(String left, String right) {
        if (left == null || right == null) {
            return false;
        }
        byte[] a = left.getBytes(StandardCharsets.UTF_8);
        byte[] b = right.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(a, b);
    }

    public static ObjectNode object(String json) {
        JsonNode node = parse(json);
        if (!node.isObject()) {
            throw RuntimeFailure.badRequest("INVALID_JSON", "json object required");
        }
        return (ObjectNode) node;
    }

    public static String mergePatch(String baseJson, String patchJson) {
        ObjectNode base = object(baseJson);
        JsonNode patch = parse(patchJson);
        if (!patch.isObject()) {
            throw RuntimeFailure.badRequest("INVALID_JSON", "patch must be object");
        }
        Iterator<Map.Entry<String, JsonNode>> fields = patch.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            base.set(field.getKey(), field.getValue());
        }
        return write(base);
    }
}
