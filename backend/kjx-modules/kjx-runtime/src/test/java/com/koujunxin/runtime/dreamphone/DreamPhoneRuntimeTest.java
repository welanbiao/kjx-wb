package com.koujunxin.runtime.dreamphone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.koujunxin.runtime.api.RuntimeFailure;
import com.koujunxin.runtime.ledger.CanonicalJson;
import com.koujunxin.runtime.support.RuntimeHarness;

class DreamPhoneRuntimeTest {

    @Test
    void trialKeepsWalletAndOnlyAcceptsTheScriptedLine() {
        RuntimeHarness harness = new RuntimeHarness();
        Map<String, Object> created = harness.app.createSession(11L, DreamPhoneWorks.CODE, true);
        String sessionId = String.valueOf(created.get("sessionId"));

        RuntimeFailure uncovered = assertThrows(RuntimeFailure.class, () -> harness.dream.action(
                11L, sessionId, Map.of("type", "send", "text", "\u968F\u4FBF\u8BF4\u4E00\u53E5")));
        assertEquals("TRIAL_UNCOVERED", uncovered.errorName());
        JsonNode blocked = payload(harness, sessionId);
        assertEquals(DreamPhoneWorks.WALLET, blocked.path("wallet").asInt());
        assertEquals(1, thread(blocked).size());

        harness.dream.action(11L, sessionId, Map.of("type", "send", "text", "\u8FD8\u597D\uFF0C\u521A\u4E0B\u73ED"));
        JsonNode played = payload(harness, sessionId);
        assertEquals(3, thread(played).size());
        assertTrue(thread(played).get(2).path("content").asText().contains("\u522B\u997F\u7740"));
        assertEquals(DreamPhoneWorks.WALLET, played.path("wallet").asInt());
        assertEquals(1, played.path("moments").size());

        assertThrows(RuntimeFailure.class, () -> harness.dream.action(
                11L, sessionId, Map.of("type", "redpacket", "amount", "1")));
        assertEquals(DreamPhoneWorks.WALLET, payload(harness, sessionId).path("wallet").asInt());
    }

    @Test
    void fullStartHasPersonaAndThreeToFiveMoments() {
        RuntimeHarness harness = new RuntimeHarness().entitle(12L, DreamPhoneWorks.CODE);
        Map<String, Object> created = harness.app.createSession(12L, DreamPhoneWorks.CODE, false);
        String sessionId = String.valueOf(created.get("sessionId"));
        JsonNode payload = payload(harness, sessionId);
        int moments = payload.path("moments").size();
        assertTrue(moments >= 3 && moments <= 5, "moments=" + moments);
        assertEquals(DreamPhoneWorks.PLAYER, payload.path("playerName").asText());
        assertEquals(DreamPhoneWorks.CHARACTER, payload.path("characterName").asText());
        assertEquals(DreamPhoneWorks.WALLET, payload.path("wallet").asDouble());
        assertTrue(payload.path("persona").path("outer").asText().length() > 0);
        assertEquals("pageLauncher", payload.path("screen").asText());
    }

    @Test
    void setScreenAndGameResultStayInSession() {
        RuntimeHarness harness = new RuntimeHarness();
        Map<String, Object> created = harness.app.createSession(13L, DreamPhoneWorks.CODE, true);
        String sessionId = String.valueOf(created.get("sessionId"));
        harness.dream.action(13L, sessionId, Map.of("type", "set-screen", "screen", "pageWechatMain"));
        assertEquals("pageWechatMain", payload(harness, sessionId).path("screen").asText());
        harness.dream.action(13L, sessionId, Map.of("type", "game-result", "game", "gomoku", "result", "win"));
        JsonNode after = payload(harness, sessionId);
        assertEquals("win", after.path("gameRecords").path("gomoku").path("lastResult").asText());
        assertEquals("pageChat", after.path("screen").asText());
        assertTrue(thread(after).get(thread(after).size() - 1).path("content").asText().contains("gomoku"));
    }

    @Test
    void resetRestoresInitialTrialPayload() {
        RuntimeHarness harness = new RuntimeHarness();
        Map<String, Object> created = harness.app.createSession(14L, DreamPhoneWorks.CODE, true);
        String sessionId = String.valueOf(created.get("sessionId"));
        harness.dream.action(14L, sessionId, Map.of("type", "send", "text", "\u8FD8\u597D\uFF0C\u521A\u4E0B\u73ED"));
        assertEquals(3, thread(payload(harness, sessionId)).size());
        harness.dream.action(14L, sessionId, Map.of("type", "reset"));
        JsonNode fresh = payload(harness, sessionId);
        assertEquals(1, thread(fresh).size());
        assertEquals(0, fresh.path("trialStep").asInt());
        assertEquals("pageLauncher", fresh.path("screen").asText());
    }

    private static JsonNode payload(RuntimeHarness harness, String sessionId) {
        return CanonicalJson.parse(harness.store.findSession(sessionId).orElseThrow().payloadJson());
    }

    private static JsonNode thread(JsonNode payload) {
        return payload.path("chatHistory").path(DreamPhoneWorks.CHARACTER_ID);
    }
}
