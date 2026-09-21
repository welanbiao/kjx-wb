package com.koujunxin.runtime.sandbox;

import java.util.Iterator;
import java.util.Locale;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.koujunxin.runtime.ledger.CanonicalJson;

/**
 * 宿主回包去掉凭据类字段，避免经 postMessage 泄漏 token/Cookie。
 */
public final class HostSanitizer {

    private static final Set<String> SECRET_KEYS = Set.of(
            "token",
            "accesstoken",
            "refreshtoken",
            "authorization",
            "cookie",
            "setcookie",
            "csrf",
            "secret",
            "apikey",
            "secretref",
            "password");

    private HostSanitizer() {
    }

    public static String sanitizeJson(String json) {
        JsonNode node = CanonicalJson.parse(json);
        return CanonicalJson.write(strip(node));
    }

    static JsonNode strip(JsonNode node) {
        if (node == null || node.isNull() || node.isValueNode()) {
            return node;
        }
        if (node.isArray()) {
            ArrayNode out = CanonicalJson.MAPPER.createArrayNode();
            for (JsonNode child : node) {
                out.add(strip(child));
            }
            return out;
        }
        ObjectNode out = CanonicalJson.MAPPER.createObjectNode();
        Iterator<String> names = node.fieldNames();
        while (names.hasNext()) {
            String name = names.next();
            if (SECRET_KEYS.contains(name.toLowerCase(Locale.ROOT).replace("-", "").replace("_", ""))) {
                continue;
            }
            out.set(name, strip(node.get(name)));
        }
        return out;
    }

    public static boolean containsSecretKey(String json) {
        String lower = json == null ? "" : json.toLowerCase(Locale.ROOT);
        return lower.contains("\"token\"")
                || lower.contains("authorization")
                || lower.contains("set-cookie")
                || lower.contains("\"cookie\"");
    }
}
