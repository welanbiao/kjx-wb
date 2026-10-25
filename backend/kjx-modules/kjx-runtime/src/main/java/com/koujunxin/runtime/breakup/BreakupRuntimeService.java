package com.koujunxin.runtime.breakup;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.koujunxin.runtime.api.RuntimeFailure;
import com.koujunxin.runtime.api.dto.SessionRef;
import com.koujunxin.runtime.api.dto.StateMutation;
import com.koujunxin.runtime.api.error.EntitlementRequiredException;
import com.koujunxin.runtime.api.port.EntitlementAccessPort;
import com.koujunxin.runtime.ledger.CanonicalJson;
import com.koujunxin.runtime.session.RuntimeStore;
import com.koujunxin.runtime.session.SessionRecord;
import com.koujunxin.runtime.session.SessionStateService;

public final class BreakupRuntimeService {

    private final BreakupCatalog catalog;
    private final BreakupSessionFactory factory;
    private final BreakupRewriteService rewrites;
    private final BreakupAssetService assets;
    private final SessionStateService sessions;
    private final RuntimeStore store;
    private final EntitlementAccessPort entitlements;

    public BreakupRuntimeService(
            BreakupCatalog catalog,
            BreakupSessionFactory factory,
            BreakupRewriteService rewrites,
            BreakupAssetService assets,
            SessionStateService sessions,
            RuntimeStore store,
            EntitlementAccessPort entitlements) {
        this.catalog = catalog;
        this.factory = factory;
        this.rewrites = rewrites;
        this.assets = assets;
        this.sessions = sessions;
        this.store = store;
        this.entitlements = entitlements;
    }

    public Map<String, Object> view(long accountId, String sessionId) {
        ObjectNode payload = loadOwned(accountId, sessionId);
        Map<String, String> urls = assets.signAll(assetKeys());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("sessionId", sessionId);
        body.put("workCode", BreakupWorks.CODE);
        body.put("trial", payload.path("trial").asBoolean(false));
        body.put("coupleLabel", BreakupWorks.COUPLE_LABEL);
        body.put("payload", CanonicalJson.parse(CanonicalJson.write(payload)));
        body.put("wall", catalog.wall());
        body.put("couple", publicCouple());
        body.put("phoneLife", catalog.phoneLife(BreakupWorks.COUPLE_ID, payload.path("side").asText("a")));
        body.put("assets", urls);
        body.put("css", Map.of(
                "fonts", assets.rewriteCss(catalog.fontsCss(), urls),
                "app", assets.rewriteCss(catalog.appCss(), urls),
                "v4", assets.rewriteCss(catalog.v4Css(), urls)));
        body.put("copy", Map.of(
                "demoUncovered", BreakupWorks.DEMO_UNCOVERED,
                "originalSend", BreakupWorks.ORIGINAL_SEND,
                "originalEdit", BreakupWorks.ORIGINAL_EDIT,
                "locked", BreakupWorks.LOCKED,
                "trialBadge", "试玩",
                "exampleEyebrow", "玩法示例"));
        return body;
    }

    public Map<String, Object> action(long accountId, String sessionId, Map<String, Object> body) {
        ObjectNode session = loadOwned(accountId, sessionId);
        String type = string(body, "type");
        switch (type) {
            case "open-couple" -> openCouple(session, string(body, "coupleId"));
            case "pick-side" -> pickSide(accountId, sessionId, session, string(body, "side"));
            case "set-worldline" -> session.put("worldline", "rewritten".equals(string(body, "worldline")) ? "rewritten" : "original");
            case "set-screen" -> session.put("screen", string(body, "screen"));
            case "monitor" -> monitor(session);
            case "release" -> session.put("ended", true);
            case "rewrite" -> {
                BreakupRewriteService.RewriteResult result = rewrites.rewrite(
                        accountId,
                        sessionId,
                        session,
                        string(body, "nodeId"),
                        string(body, "msgId"),
                        string(body, "text"),
                        optional(body, "apiKey"),
                        optional(body, "apiBase"),
                        optional(body, "apiModel"));
                persist(accountId, sessionId, result.session());
                invokeSharedTools(accountId, sessionId, result);
                Map<String, Object> view = view(accountId, sessionId);
                view.put("replies", result.replies());
                view.put("ripple", result.ripple());
                view.put("worldMoved", result.worldMoved());
                return view;
            }
            case "send" -> {
                if ("original".equals(session.path("worldline").asText())) {
                    throw RuntimeFailure.badRequest("ORIGINAL_LOCKED", BreakupWorks.ORIGINAL_SEND);
                }
                BreakupRewriteService.RewriteResult result = rewrites.send(
                        accountId,
                        sessionId,
                        session,
                        string(body, "text"),
                        optional(body, "apiKey"),
                        optional(body, "apiBase"),
                        optional(body, "apiModel"));
                persist(accountId, sessionId, result.session());
                invokeSharedTools(accountId, sessionId, result);
                Map<String, Object> view = view(accountId, sessionId);
                view.put("replies", result.replies());
                return view;
            }
            case "redpacket" -> {
                rewrites.callRedpacket(accountId, sessionId, session, Double.parseDouble(string(body, "amount")), optional(body, "note"));
                session = loadOwned(accountId, sessionId);
            }
            default -> throw RuntimeFailure.badRequest("INVALID_MUTATION", "unknown breakup action");
        }
        persist(accountId, sessionId, session);
        return view(accountId, sessionId);
    }

    public byte[] asset(String key, long exp, String sig) {
        return assets.read(key, exp, sig);
    }

    String initialPayload(boolean trial) {
        return factory.initialPayload(trial);
    }

    private void openCouple(ObjectNode session, String coupleId) {
        JsonNode slide = null;
        for (JsonNode item : catalog.wall().path("slides")) {
            if (coupleId.equals(item.path("id").asText())) {
                slide = item;
                break;
            }
        }
        if (slide == null || slide.path("locked").asBoolean(false) || !slide.path("playable").asBoolean(false)) {
            throw RuntimeFailure.badRequest("COUPLE_LOCKED", BreakupWorks.LOCKED);
        }
        if (!BreakupWorks.COUPLE_ID.equals(coupleId)) {
            throw RuntimeFailure.badRequest("COUPLE_LOCKED", BreakupWorks.LOCKED);
        }
        session.put("coupleId", coupleId);
        session.put("screen", "screen-side");
    }

    private void pickSide(long accountId, String sessionId, ObjectNode session, String side) {
        if (!"a".equals(side) && !"b".equals(side)) {
            throw RuntimeFailure.badRequest("INVALID_MUTATION", "side must be a or b");
        }
        rewrites.seedCharacters(session, catalog.asphyxia(), side);
        session.put("screen", "screen-hook");
    }

    private static void monitor(ObjectNode session) {
        int count = session.path("monitorCount").asInt(0) + 1;
        session.put("monitorCount", count);
        if (count >= 3) {
            session.put("monitorCaught", session.path("monitorCaught").asInt(0) + 1);
            session.put("msgBlocked", true);
        }
        session.put("screen", "screen-monitor");
    }

    private ObjectNode loadOwned(long accountId, String sessionId) {
        SessionRef ref = new SessionRef(sessionId, BreakupWorks.CODE);
        sessions.requireOwner(accountId, ref);
        SessionRecord row = store.findSession(sessionId).orElseThrow(RuntimeFailure::crossSession);
        if (!BreakupWorks.trialPayload(row.payloadJson())) {
            entitlements.requireEntitlement(accountId, BreakupWorks.CODE);
        }
        JsonNode payload = CanonicalJson.parse(row.payloadJson());
        if (!(payload instanceof ObjectNode objectNode) || !objectNode.path("work").asText().equals(BreakupWorks.CODE)) {
            throw RuntimeFailure.badRequest("SESSION_WORK_MISMATCH", "not a breakup session");
        }
        return objectNode;
    }

    private void invokeSharedTools(long accountId, String sessionId, BreakupRewriteService.RewriteResult result) {
        if (result.session().path("trial").asBoolean(false)) {
            return;
        }
        ObjectNode current = loadOwned(accountId, sessionId);
        rewrites.assemblePersona(accountId, sessionId, current);
        for (String reply : result.replies()) {
            rewrites.callChatSend(accountId, sessionId, current, reply);
        }
    }

    private void persist(long accountId, String sessionId, ObjectNode session) {
        if (session.path("trial").asBoolean(false)) {
            session.set("achievements", CanonicalJson.MAPPER.createArrayNode());
        }
        SessionRecord row = store.findSession(sessionId).orElseThrow(RuntimeFailure::crossSession);
        sessions.apply(
                new SessionRef(sessionId, BreakupWorks.CODE),
                new StateMutation("REPLACE", row.schemaVersion(), CanonicalJson.write(session)));
    }

    private JsonNode publicCouple() {
        ObjectNode couple = catalog.coupleCopy();
        couple.remove("truth");
        couple.remove("truthClues");
        return couple;
    }

    private List<String> assetKeys() {
        Set<String> keys = new LinkedHashSet<>();
        collectAssetStrings(catalog.wall(), keys);
        collectAssetStrings(catalog.asphyxia(), keys);
        collectAssetText(catalog.appCss(), keys);
        collectAssetText(catalog.v4Css(), keys);
        collectAssetText(catalog.fontsCss(), keys);
        keys.add("fonts/noto-serif-sc-600-subset.woff2");
        keys.add("audio/home-bgm.mp3");
        keys.add("audio/prologue-bgm.mp3");
        keys.add("wall2/device-chrome-full.svg");
        keys.add("wall2/icon-back.svg");
        keys.add("wallpaper-dusk.webp");
        keys.add("wallpaper-asphyxia-him-home.webp");
        keys.add("wallpaper-asphyxia-her.webp");
        keys.add("wallpaper-asphyxia-him.webp");
        keys.add("wall-phone-complete-asphyxia-left.webp");
        keys.add("wall-phone-complete-asphyxia-right.webp");
        for (String name : List.of(
                "figma-album-him.webp",
                "figma-dynamic-island.svg",
                "figma-explicit.svg",
                "figma-music-note.svg",
                "figma-pause.svg",
                "figma-play.svg",
                "figma-ring-a.svg",
                "figma-ring-b.svg",
                "figma-ring-dot.svg",
                "figma-status-icons-right.svg",
                "glyph-chat-bubble.svg",
                "glyph-chat-dot.svg",
                "glyph-chat-stem.svg",
                "glyph-contacts-body.svg",
                "glyph-contacts-head.svg",
                "glyph-moments.svg",
                "glyph-monitor.svg",
                "glyph-notes.svg",
                "glyph-photos.svg",
                "glyph-settings.svg",
                "glyph-wallet.svg")) {
            keys.add("home/" + name);
        }
        return new ArrayList<>(keys);
    }

    private static void collectAssetStrings(JsonNode node, Set<String> keys) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return;
        }
        if (node.isTextual()) {
            String value = node.asText();
            if (value.contains("assets/") || value.endsWith(".webp") || value.endsWith(".svg")
                    || value.endsWith(".png") || value.endsWith(".mp3") || value.endsWith(".woff2")) {
                keys.add(BreakupAssetService.normalize(value));
            }
            return;
        }
        if (node.isArray() || node.isObject()) {
            node.forEach(child -> collectAssetStrings(child, keys));
        }
    }

    private static void collectAssetText(String text, Set<String> keys) {
        if (text == null || text.isBlank()) {
            return;
        }
        int from = 0;
        while (from < text.length()) {
            int idx = text.indexOf("assets/", from);
            if (idx < 0) {
                break;
            }
            int start = idx + "assets/".length();
            int end = start;
            while (end < text.length()) {
                char ch = text.charAt(end);
                if (ch == '"' || ch == '\'' || ch == ')' || ch == '?' || Character.isWhitespace(ch)) {
                    break;
                }
                end++;
            }
            if (end > start) {
                keys.add(BreakupAssetService.normalize(text.substring(idx, end)));
            }
            from = end + 1;
        }
    }

    private static String string(Map<String, Object> body, String key) {
        Object value = body == null ? null : body.get(key);
        if (value == null || String.valueOf(value).isBlank()) {
            throw RuntimeFailure.badRequest("INVALID_MUTATION", key + " is required");
        }
        return String.valueOf(value);
    }

    private static String optional(Map<String, Object> body, String key) {
        Object value = body == null ? null : body.get(key);
        return value == null ? "" : String.valueOf(value);
    }

    public static boolean entitled(EntitlementAccessPort entitlements, long accountId, String workCode) {
        try {
            entitlements.requireEntitlement(accountId, workCode);
            return true;
        } catch (EntitlementRequiredException ex) {
            return false;
        }
    }
}
