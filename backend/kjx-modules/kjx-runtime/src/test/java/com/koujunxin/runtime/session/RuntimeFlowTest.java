package com.koujunxin.runtime.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.koujunxin.runtime.api.RuntimeFailure;
import com.koujunxin.runtime.api.dto.SessionRef;
import com.koujunxin.runtime.api.dto.StateMutation;
import com.koujunxin.runtime.api.dto.ToolResult;
import com.koujunxin.runtime.api.error.EntitlementRequiredException;
import com.koujunxin.runtime.api.error.SessionNotOwnedException;
import com.koujunxin.runtime.support.RuntimeHarness;

class RuntimeFlowTest {

    @Test
    void enterDialogueToolAndSave() {
        RuntimeHarness harness = new RuntimeHarness().entitle(11L, "phone-demo");
        AtomicInteger tools = new AtomicInteger();
        harness.tools = command -> {
            tools.incrementAndGet();
            harness.sessions.apply(
                    command.session(),
                    new StateMutation("PATCH", "1", "{\"lastTool\":\"" + command.toolCode() + "\"}"));
            return ToolResult.ok("{\"applied\":true}");
        };
        Map<String, Object> created = harness.app.createSession(11L, "phone-demo");
        String sessionId = (String) created.get("sessionId");
        Map<String, Object> stage = harness.app.stage(11L, sessionId, "phone-demo");
        assertEquals(Boolean.FALSE, stage.get("gated"));
        assertTrue(String.valueOf(stage.get("layoutJson")).contains("第一幕"));
        assertFalse(String.valueOf(stage.get("layoutJson")).contains("第二幕尚未到达"));
        assertTrue(String.valueOf(stage.get("nodesJson")).contains("hello"));
        assertFalse(String.valueOf(stage.get("nodesJson")).contains("secret-ending"));

        harness.app.apply(11L, sessionId, "phone-demo", new StateMutation("PATCH", "1", "{\"lastUserMessage\":\"继续\"}"));
        Map<String, Object> tool = harness.app.invokeToolHttp(
                11L, sessionId, "phone-demo", null, "null",
                "{\"toolCode\":\"chat.send\",\"toolVersion\":\"1\",\"argumentsJson\":\"{\\\"text\\\":\\\"继续\\\"}\"}");
        assertEquals(1, tools.get());
        assertTrue(String.valueOf(tool.get("payloadJson")).contains("applied"));
        Map<String, Object> saved = harness.app.save(11L, sessionId, "phone-demo", "auto", null);
        assertEquals("auto", saved.get("slot"));
        assertTrue(String.valueOf(harness.sessions.load(new SessionRef(sessionId, "phone-demo")).payloadJson()).contains("chat.send"));
        assertEquals(1, harness.app.listSaves(11L, sessionId, "phone-demo").size());
    }

    @Test
    void expiredStyleEntitlementStillPlaysAndMissingEntitlementIsGated() {
        RuntimeHarness harness = new RuntimeHarness().entitle(11L, "phone-demo");
        Map<String, Object> created = harness.app.createSession(11L, "phone-demo");
        String sessionId = (String) created.get("sessionId");
        harness.app.save(11L, sessionId, "phone-demo", "auto", null);
        Map<String, Object> stage = harness.app.stage(11L, sessionId, "phone-demo");
        assertEquals(Boolean.FALSE, stage.get("gated"));

        RuntimeHarness stranger = new RuntimeHarness();
        stranger.store.insertSession(harness.store.findSession(sessionId).orElseThrow());
        Map<String, Object> gated = stranger.app.stage(11L, sessionId, "phone-demo");
        assertEquals(Boolean.TRUE, gated.get("gated"));
        assertEquals("{}", gated.get("layoutJson"));
        assertEquals("[]", gated.get("nodesJson"));
        assertThrows(EntitlementRequiredException.class, () -> stranger.app.save(11L, sessionId, "phone-demo", "auto", null));
    }

    @Test
    void rejectsCrossWorkAccountAndSession() {
        RuntimeHarness harness = new RuntimeHarness().entitle(11L, "phone-demo").entitle(12L, "phone-demo").entitle(11L, "sandbox-demo");
        Map<String, Object> created = harness.app.createSession(11L, "phone-demo");
        String sessionId = (String) created.get("sessionId");
        assertThrows(RuntimeFailure.class, () -> harness.app.getSession(11L, sessionId, "sandbox-demo"));
        assertThrows(SessionNotOwnedException.class, () -> harness.app.getSession(12L, sessionId, "phone-demo"));
        assertThrows(RuntimeFailure.class, () -> harness.app.getSession(11L, "missing-session", "phone-demo"));
    }

    @Test
    void sandboxBootstrapHasNonceAndNoToken() {
        RuntimeHarness harness = new RuntimeHarness().entitle(11L, "sandbox-demo");
        Map<String, Object> created = harness.app.createSession(11L, "sandbox-demo");
        String sessionId = (String) created.get("sessionId");
        Map<String, Object> boot = harness.app.sandboxBootstrap(11L, sessionId, "sandbox-demo");
        assertTrue(String.valueOf(boot.get("sandbox")).equals("allow-scripts allow-forms"));
        assertFalse(String.valueOf(boot).toLowerCase().contains("token"));
        assertFalse(String.valueOf(boot).toLowerCase().contains("cookie"));
        String nonce = (String) boot.get("nonce");
        Map<String, Object> saved = harness.app.hostInvoke(
                11L, sessionId, "sandbox-demo", nonce, "https://demo.game.example", "state.save", "{\"slot\":\"auto\"}");
        assertEquals(Boolean.TRUE, saved.get("ok"));
        assertThrows(RuntimeFailure.class, () -> harness.app.hostInvoke(
                11L, sessionId, "sandbox-demo", nonce, "https://demo.game.example", "eval", "{}"));
    }

    @Test
    void toolPatchReplacesSessionPayload() {
        RuntimeHarness harness = new RuntimeHarness().entitle(11L, "phone-demo");
        Map<String, Object> created = harness.app.createSession(11L, "phone-demo");
        String sessionId = (String) created.get("sessionId");
        harness.app.apply(11L, sessionId, "phone-demo", new StateMutation(
                "TOOL_PATCH", "1", "{\"flags\":{},\"reached\":[\"1\"],\"stack\":[\"home\"],\"score\":3}"));
        assertTrue(harness.sessions.load(new SessionRef(sessionId, "phone-demo")).payloadJson().contains("\"score\":3"));
    }

    @Test
    void schemaMismatchOnMutation() {
        RuntimeHarness harness = new RuntimeHarness().entitle(11L, "phone-demo");
        Map<String, Object> created = harness.app.createSession(11L, "phone-demo");
        String sessionId = (String) created.get("sessionId");
        RuntimeFailure failure = assertThrows(RuntimeFailure.class, () -> harness.app.apply(
                11L, sessionId, "phone-demo", new StateMutation("PATCH", "9", "{\"x\":1}")));
        assertEquals("SCHEMA_MISMATCH", failure.errorName());
    }
}
