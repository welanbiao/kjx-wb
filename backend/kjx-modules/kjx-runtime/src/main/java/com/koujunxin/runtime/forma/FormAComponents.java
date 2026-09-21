package com.koujunxin.runtime.forma;

import java.util.Set;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;
import com.koujunxin.runtime.api.RuntimeFailure;
import com.koujunxin.runtime.ledger.CanonicalJson;

/**
 * Form A 固定组件白名单。未知 type 拒绝下发，不开放作品扩展。
 */
public final class FormAComponents {

    public static final Set<String> TYPES = Set.of(
            "chat.bubble",
            "chat.image",
            "chat.voice",
            "chat.quote",
            "chat.system",
            "chat.typing",
            "chat.stream-cursor",
            "chat.read-receipt",
            "redpacket-card",
            "transfer-card",
            "music-card",
            "game-invite-card",
            "link-card",
            "moment-card",
            "moment-image-grid",
            "moment-comment",
            "moment-like",
            "photo-grid",
            "photo-viewer",
            "memo-list",
            "memo-item",
            "wallet-balance",
            "wallet-bill-item",
            "order-card",
            "order-timeline",
            "order-detail",
            "music-player",
            "music-list",
            "listen-together",
            "board-gomoku",
            "rps-panel",
            "guess-number",
            "word-guess",
            "dialog",
            "sheet",
            "toast",
            "badge",
            "tabs",
            "list",
            "empty-state",
            "desktop-icon",
            "status-bar",
            "nav-stack",
            "app-frame");

    private static final Pattern SLOT = Pattern.compile("^custom\\.slot:[A-Za-z0-9_-]{1,32}$");
    private static final Pattern DANGEROUS_KEY = Pattern.compile("(?i)^(on[a-z].*|script|dangerouslysetinnerhtml)$");

    private FormAComponents() {
    }

    public static boolean allowed(String type) {
        if (type == null || type.isBlank()) {
            return false;
        }
        return TYPES.contains(type) || SLOT.matcher(type).matches();
    }

    public static void validateLayout(String layoutJson) {
        rejectDangerous(CanonicalJson.parse(layoutJson));
        JsonNode root = CanonicalJson.parse(layoutJson);
        JsonNode screens = root.get("screens");
        if (screens == null || !screens.isArray()) {
            return;
        }
        for (JsonNode screen : screens) {
            JsonNode nodes = screen.get("nodes");
            if (nodes == null || !nodes.isArray()) {
                continue;
            }
            for (JsonNode node : nodes) {
                String type = node.path("type").asText("");
                if (!allowed(type)) {
                    throw RuntimeFailure.badRequest("UNKNOWN_COMPONENT", "component not in Form A whitelist: " + type);
                }
            }
        }
    }

    static void rejectDangerous(JsonNode node) {
        if (node == null) {
            return;
        }
        if (node.isObject()) {
            var fields = node.fields();
            while (fields.hasNext()) {
                var field = fields.next();
                if (DANGEROUS_KEY.matcher(field.getKey()).matches()) {
                    throw RuntimeFailure.badRequest("UNSAFE_LAYOUT", "layout key rejected: " + field.getKey());
                }
                rejectDangerous(field.getValue());
            }
        } else if (node.isArray()) {
            for (JsonNode child : node) {
                rejectDangerous(child);
            }
        }
    }
}
