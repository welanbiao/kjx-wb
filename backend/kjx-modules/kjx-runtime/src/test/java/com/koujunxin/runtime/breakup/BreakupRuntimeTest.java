package com.koujunxin.runtime.breakup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.koujunxin.runtime.api.RuntimeAiPort;
import com.koujunxin.runtime.api.RuntimeFailure;
import com.koujunxin.runtime.api.dto.StateMutation;
import com.koujunxin.runtime.api.dto.ToolResult;
import com.koujunxin.runtime.api.error.EntitlementRequiredException;
import com.koujunxin.runtime.api.port.AccountAiSettingsPort;
import com.koujunxin.runtime.ledger.CanonicalJson;
import com.koujunxin.runtime.support.RuntimeHarness;

class BreakupRuntimeTest {

    @Test
    void coupleLabelUsesPlanCodepointNotLookalike() {
        assertEquals(0x65F8, BreakupWorks.COUPLE_LABEL.codePointAt(6));
        assertNotEquals(0x6600, BreakupWorks.COUPLE_LABEL.codePointAt(6));
        assertEquals("\u6C5F\u96EA \u00D7 \u5F20\u65F8\u5955", BreakupWorks.COUPLE_LABEL);
        JsonNode wall = new BreakupCatalog().wall();
        assertEquals(BreakupWorks.COUPLE_LABEL, wall.path("coupleLabel").asText());
        assertEquals(BreakupWorks.COUPLE_LABEL, wall.path("slides").get(0).path("couple").asText());
    }

    @Test
    void trialTwoScriptedRewritesThirdUncoveredAndNoTools() {
        RuntimeHarness harness = new RuntimeHarness();
        AtomicInteger tools = new AtomicInteger();
        harness.tools = command -> {
            tools.incrementAndGet();
            return ToolResult.ok("{}");
        };
        Map<String, Object> created = harness.app.createSession(11L, BreakupWorks.CODE, true);
        String sessionId = (String) created.get("sessionId");
        enterRewritten(harness, 11L, sessionId);

        Map<String, Object> first = harness.breakup.action(11L, sessionId, Map.of(
                "type", "rewrite",
                "nodeId", "n7",
                "msgId", "n7g",
                "text", "对不起，别走。我想你。"));
        assertEquals(Boolean.TRUE, first.get("worldMoved"));
        JsonNode payload = payload(harness, sessionId);
        assertTrue(payload.path("trial").asBoolean());
        assertTrue(payload.path("world").path("threadPatches").path("juan").path("preview").asText().contains("和好"));
        assertTrue(payload.toString().contains("对不起，别走"));
        assertEquals(0, payload.path("achievements").size());

        Map<String, Object> second = harness.breakup.action(11L, sessionId, Map.of(
                "type", "rewrite",
                "nodeId", "n15",
                "msgId", "n15g",
                "text", "抱歉，我想你。"));
        assertEquals(Boolean.TRUE, second.get("worldMoved"));

        RuntimeFailure uncovered = assertThrows(RuntimeFailure.class, () -> harness.breakup.action(11L, sessionId, Map.of(
                "type", "rewrite",
                "nodeId", "n6",
                "msgId", "n6g",
                "text", "对不起。")));
        assertEquals("TRIAL_UNCOVERED", uncovered.errorName());
        assertEquals(BreakupWorks.DEMO_UNCOVERED, uncovered.getMessage());
        JsonNode after = payload(harness, sessionId);
        assertEquals(2, after.path("edits").size());
        assertEquals(0, tools.get());
        assertFalse(after.toString().contains("sk-"));
    }

    @Test
    void trialDoesNotTouchFullSessionOrQuotaFields() {
        RuntimeHarness harness = new RuntimeHarness();
        String trialId = (String) harness.app.createSession(11L, BreakupWorks.CODE, true).get("sessionId");
        enterRewritten(harness, 11L, trialId);
        harness.breakup.action(11L, trialId, Map.of(
                "type", "rewrite", "nodeId", "n7", "msgId", "n7g", "text", "对不起，别走。"));

        harness.entitle(11L, BreakupWorks.CODE);
        String fullId = (String) harness.app.createSession(11L, BreakupWorks.CODE, false).get("sessionId");
        assertNotEquals(trialId, fullId);
        JsonNode full = payload(harness, fullId);
        assertFalse(full.path("trial").asBoolean());
        assertEquals(0, full.path("edits").size());
        assertFalse(full.toString().contains("对不起，别走"));
        JsonNode trial = payload(harness, trialId);
        assertTrue(trial.path("trial").asBoolean());
        assertEquals(1, trial.path("edits").size());
    }

    @Test
    void checksumSaveRoundTripAndOriginalWorldBlocksRewrite() {
        RuntimeHarness harness = new RuntimeHarness();
        String sessionId = (String) harness.app.createSession(11L, BreakupWorks.CODE, true).get("sessionId");
        enterRewritten(harness, 11L, sessionId);
        harness.breakup.action(11L, sessionId, Map.of(
                "type", "rewrite", "nodeId", "n7", "msgId", "n7g", "text", "对不起。"));
        Map<String, Object> saved = harness.app.save(11L, sessionId, BreakupWorks.CODE, "auto", null);
        var row = harness.store.findSession(sessionId).orElseThrow();
        assertEquals(row.checksum(), saved.get("checksum"));
        CanonicalJson.verifyChecksum(
                row.schemaVersion(), row.contentVersion(), row.stage(), row.payloadJson(), row.checksum());
        List<Map<String, Object>> slots = harness.app.listSaves(11L, sessionId, BreakupWorks.CODE);
        assertEquals(1, slots.size());
        assertEquals(row.checksum(), slots.getFirst().get("checksum"));

        harness.breakup.action(11L, sessionId, Map.of("type", "set-worldline", "worldline", "original"));
        RuntimeFailure locked = assertThrows(RuntimeFailure.class, () -> harness.breakup.action(11L, sessionId, Map.of(
                "type", "rewrite", "nodeId", "n15", "msgId", "n15g", "text", "对不起。")));
        assertEquals("ORIGINAL_LOCKED", locked.errorName());
    }

    @Test
    void invalidMessageAndTitleFreezeRejectWithoutMutating() {
        RuntimeHarness harness = new RuntimeHarness();
        String sessionId = (String) harness.app.createSession(11L, BreakupWorks.CODE, true).get("sessionId");
        enterRewritten(harness, 11L, sessionId);
        String before = payload(harness, sessionId).toString();
        RuntimeFailure missing = assertThrows(RuntimeFailure.class, () -> harness.breakup.action(11L, sessionId, Map.of(
                "type", "rewrite", "nodeId", "n7", "msgId", "no-such", "text", "对不起。")));
        assertEquals("PACK_INVALID", missing.errorName());
        assertEquals(before, payload(harness, sessionId).toString());

        RuntimeHarness titled = new RuntimeHarness().entitle(12L, BreakupWorks.CODE);
        RuntimeAiPort ai = (accountId, sid, work, payloadJson) -> "{\"title\":\"改了\",\"from\":\"n7\",\"next\":\"n10\",\"chat\":{\"mode\":\"KEEP\"}}";
        BreakupRuntimeService service = new BreakupRuntimeService(
                titled.breakupCatalog,
                titled.breakupSessions,
                new BreakupRewriteService(titled.breakupCatalog, command -> ToolResult.ok("{}"), ai),
                new BreakupAssetService("unit-test-runtime-secret"),
                titled.sessions,
                titled.store,
                titled.entitlements);
        String fullId = (String) titled.app.createSession(12L, BreakupWorks.CODE, false).get("sessionId");
        enterRewritten(titled, 12L, fullId);
        String frozen = payload(titled, fullId).path("titles").get(0).path("title").asText();
        RuntimeFailure title = assertThrows(RuntimeFailure.class, () -> service.action(12L, fullId, Map.of(
                "type", "rewrite",
                "nodeId", "n7",
                "msgId", "n7g",
                "text", "对不起。",
                "apiKey", "sk-unit-test-key",
                "apiBase", "https://gw.example/v1",
                "apiModel", "user-model")));
        assertEquals("PACK_INVALID", title.errorName());
        JsonNode after = payload(titled, fullId);
        assertEquals(frozen, after.path("titles").get(0).path("title").asText());
        assertEquals(frozen, after.path("rewritten").path("nodes").get(0).path("title").asText());
        assertFalse(after.toString().contains("sk-unit-test-key"));
        assertEquals(0, after.path("edits").size());
    }

    @Test
    void lockedCoupleAndSoftHarshRules() {
        RuntimeHarness harness = new RuntimeHarness();
        String sessionId = (String) harness.app.createSession(11L, BreakupWorks.CODE, true).get("sessionId");
        RuntimeFailure locked = assertThrows(RuntimeFailure.class, () -> harness.breakup.action(11L, sessionId, Map.of(
                "type", "open-couple", "coupleId", "growth")));
        assertEquals("COUPLE_LOCKED", locked.errorName());
        assertEquals(BreakupWorks.LOCKED, locked.getMessage());

        assertEquals("soft", BreakupWorldRules.tone("对不起，我想你"));
        assertEquals("harsh", BreakupWorldRules.tone("滚，别联系"));
        assertEquals("none", BreakupWorldRules.tone("今天联考砸了"));
    }

    @Test
    void fullRequiresEntitlementTrialDoesNotAndKeysStayOutOfSession() {
        RuntimeHarness harness = new RuntimeHarness();
        assertThrows(EntitlementRequiredException.class, () -> harness.app.createSession(11L, BreakupWorks.CODE, false));
        Map<String, Object> trial = harness.app.createSession(11L, BreakupWorks.CODE, true);
        assertTrue(String.valueOf(trial.get("payloadJson")).contains("\"trial\":true"));

        harness.entitle(11L, BreakupWorks.CODE);
        String fullId = (String) harness.app.createSession(11L, BreakupWorks.CODE, false).get("sessionId");
        enterRewritten(harness, 11L, fullId);
        RuntimeFailure key = assertThrows(RuntimeFailure.class, () -> harness.breakup.action(11L, fullId, Map.of(
                "type", "rewrite", "nodeId", "n7", "msgId", "n7g", "text", "对不起。")));
        assertEquals("KEY_REQUIRED", key.errorName());

        assertThrows(RuntimeFailure.class, () -> harness.app.apply(
                11L, fullId, BreakupWorks.CODE,
                new StateMutation("PATCH", "1", "{\"trial\":true}")));
    }

    @Test
    void fullRewriteSendsSavedByokSettingsWithoutPlatformFallback() {
        RuntimeHarness titled = new RuntimeHarness().entitle(12L, BreakupWorks.CODE);
        AtomicInteger calls = new AtomicInteger();
        java.util.concurrent.atomic.AtomicReference<String> seen = new java.util.concurrent.atomic.AtomicReference<>();
        RuntimeAiPort ai = (accountId, sid, work, payloadJson) -> {
            calls.incrementAndGet();
            seen.set(payloadJson);
            return "{\"from\":\"n7\"}";
        };
        AccountAiSettingsPort settings = new AccountAiSettingsPort() {
            @Override
            public void save(long accountId, String baseUrl, String apiKey, String model) {
            }

            @Override
            public java.util.Optional<AccountAiSettingsPort.Snapshot> find(long accountId) {
                return java.util.Optional.of(new AccountAiSettingsPort.Snapshot(
                        "https://gw.example/v1", "sk-saved-only", "user-model"));
            }
        };
        BreakupRuntimeService service = new BreakupRuntimeService(
                titled.breakupCatalog,
                titled.breakupSessions,
                new BreakupRewriteService(titled.breakupCatalog, command -> ToolResult.ok("{}"), ai, settings),
                new BreakupAssetService("unit-test-runtime-secret"),
                titled.sessions,
                titled.store,
                titled.entitlements);
        String fullId = (String) titled.app.createSession(12L, BreakupWorks.CODE, false).get("sessionId");
        enterRewritten(titled, 12L, fullId);
        assertThrows(RuntimeFailure.class, () -> service.action(12L, fullId, Map.of(
                "type", "rewrite", "nodeId", "n7", "msgId", "n7g", "text", "对不起。")));
        assertEquals(1, calls.get());
        String payloadJson = seen.get();
        assertTrue(payloadJson.contains("\"channel\":\"BYOK\""));
        assertTrue(payloadJson.contains("https://gw.example/v1"));
        assertTrue(payloadJson.contains("sk-saved-only"));
        assertTrue(payloadJson.contains("user-model"));
        assertFalse(payloadJson.contains("PLATFORM"));
        assertFalse(payload(titled, fullId).toString().contains("sk-saved-only"));
    }

    @Test
    void fullSendCallsSavedByokAndAppendsReplyIntoChatNodes() {
        RuntimeHarness titled = new RuntimeHarness().entitle(12L, BreakupWorks.CODE);
        AtomicInteger calls = new AtomicInteger();
        java.util.concurrent.atomic.AtomicReference<String> seen = new java.util.concurrent.atomic.AtomicReference<>();
        RuntimeAiPort ai = (accountId, sid, work, payloadJson) -> {
            calls.incrementAndGet();
            seen.set(payloadJson);
            return "{\"text\":\"«REPLY:我在。»\"}";
        };
        AccountAiSettingsPort settings = savedByok();
        BreakupRuntimeService service = new BreakupRuntimeService(
                titled.breakupCatalog,
                titled.breakupSessions,
                new BreakupRewriteService(titled.breakupCatalog, command -> ToolResult.ok("{}"), ai, settings),
                new BreakupAssetService("unit-test-runtime-secret"),
                titled.sessions,
                titled.store,
                titled.entitlements);
        String fullId = (String) titled.app.createSession(12L, BreakupWorks.CODE, false).get("sessionId");
        enterRewritten(titled, 12L, fullId);
        int before = payload(titled, fullId).path("rewritten").path("nodes").get(0).path("chat").size();
        Map<String, Object> view = service.action(12L, fullId, Map.of("type", "send", "text", "你还在吗"));
        assertEquals(1, calls.get());
        assertTrue(seen.get().contains("https://gw.example/v1"));
        assertTrue(seen.get().contains("sk-saved-only"));
        JsonNode after = payload(titled, fullId);
        String chat = after.path("rewritten").toString();
        assertTrue(chat.contains("你还在吗"));
        assertTrue(chat.contains("我在。"));
        assertTrue(after.path("rewritten").path("nodes").get(after.path("rewritten").path("nodes").size() - 1)
                .path("chat").size() > before || chat.contains("我在。"));
        @SuppressWarnings("unchecked")
        Map<String, String> assets = (Map<String, String>) view.get("assets");
        assertTrue(assets.containsKey("wall-phone-complete-asphyxia-left.webp"));
        assertTrue(assets.get("wall-phone-complete-asphyxia-left.webp").contains("/app/works/breakup-editor/assets"));
        assertFalse(after.toString().contains("sk-saved-only"));
    }

    @Test
    void fullSendAiFailureKeepsOldLinesAndThrows() {
        RuntimeHarness titled = new RuntimeHarness().entitle(12L, BreakupWorks.CODE);
        RuntimeAiPort ai = (accountId, sid, work, payloadJson) ->
                "{\"code\":\"AI_FAILED\",\"message\":\"网关超时\"}";
        BreakupRuntimeService service = new BreakupRuntimeService(
                titled.breakupCatalog,
                titled.breakupSessions,
                new BreakupRewriteService(titled.breakupCatalog, command -> ToolResult.ok("{}"), ai, savedByok()),
                new BreakupAssetService("unit-test-runtime-secret"),
                titled.sessions,
                titled.store,
                titled.entitlements);
        String fullId = (String) titled.app.createSession(12L, BreakupWorks.CODE, false).get("sessionId");
        enterRewritten(titled, 12L, fullId);
        String before = payload(titled, fullId).path("rewritten").toString();
        RuntimeFailure failed = assertThrows(RuntimeFailure.class, () -> service.action(12L, fullId, Map.of(
                "type", "send", "text", "你还在吗")));
        assertEquals("AI_FAILED", failed.errorName());
        assertTrue(failed.getMessage().contains("超时"));
        assertEquals(before, payload(titled, fullId).path("rewritten").toString());
        assertFalse(payload(titled, fullId).toString().contains("你还在吗"));
    }

    private static AccountAiSettingsPort savedByok() {
        return new AccountAiSettingsPort() {
            @Override
            public void save(long accountId, String baseUrl, String apiKey, String model) {
            }

            @Override
            public java.util.Optional<AccountAiSettingsPort.Snapshot> find(long accountId) {
                return java.util.Optional.of(new AccountAiSettingsPort.Snapshot(
                        "https://gw.example/v1", "sk-saved-only", "user-model"));
            }
        };
    }

    private static void enterRewritten(RuntimeHarness harness, long accountId, String sessionId) {
        harness.breakup.action(accountId, sessionId, Map.of("type", "open-couple", "coupleId", "asphyxia"));
        harness.breakup.action(accountId, sessionId, Map.of("type", "pick-side", "side", "a"));
        harness.breakup.action(accountId, sessionId, Map.of("type", "set-worldline", "worldline", "rewritten"));
    }

    private static JsonNode payload(RuntimeHarness harness, String sessionId) {
        return CanonicalJson.parse(harness.store.findSession(sessionId).orElseThrow().payloadJson());
    }
}
