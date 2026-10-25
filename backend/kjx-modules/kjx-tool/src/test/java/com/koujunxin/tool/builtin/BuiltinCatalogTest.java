package com.koujunxin.tool.builtin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.koujunxin.runtime.api.dto.SessionRef;
import com.koujunxin.runtime.api.dto.SessionStateSnapshot;
import com.koujunxin.tool.api.ToolFailure;
import com.koujunxin.tool.state.WorkingState;

class BuiltinCatalogTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final BuiltinCatalog catalog = new BuiltinCatalog(mapper, Clock.systemUTC());

    @Test
    void walletAndRedpacketMutateWorkingState() throws Exception {
        WorkingState state = new WorkingState(mapper, snapshot("{\"wallet\":100}"));
        catalog.execute("wallet.adjust", mapper.readTree("{\"delta\":-10}"), state);
        catalog.execute("redpacket.send", mapper.readTree("{\"amount\":20,\"note\":\"cake\"}"), state);
        assertEquals(70.0, state.payload().path("wallet").asDouble());
        assertEquals(3, state.ui().size());
        assertTrue(state.dirty());
    }

    @Test
    void chatSendProducesWhitelistedBubble() {
        WorkingState state = new WorkingState(mapper, snapshot("{}"));
        catalog.execute("chat.send", mapper.createObjectNode().put("content", "hi"), state);
        assertEquals("chat.bubble", state.ui().get(0).path("node").path("type").asText());
        JsonNode line = state.payload().path("messages").get(0);
        assertEquals("direct", line.path("channel").asText());
        assertEquals("npc", line.path("role").asText());
        assertEquals("hi", line.path("text").asText());
        assertTrue(state.dirty());
    }

    @Test
    void chatSendRejectsEmptyContentWithoutMutation() {
        WorkingState state = new WorkingState(mapper, snapshot("{}"));
        ToolFailure failure = assertThrows(ToolFailure.class,
                () -> catalog.execute("chat.send", mapper.createObjectNode().put("content", "  "), state));
        assertEquals("PRECONDITION_FAILED", failure.code());
        assertFalse(state.payload().has("messages"));
        assertEquals(0, state.ui().size());
        assertFalse(state.dirty());
    }

    @Test
    void personaAssembleStoresModelOnlyFragment() throws Exception {
        WorkingState state = new WorkingState(mapper, snapshot("""
                {"persona":{"outer":"calm","inner":"guarded","origin":"a small town","speech":"short sentences","triggers":"silence","redlines":"no insults"}}
                """));
        catalog.execute("persona.assemble", mapper.createObjectNode(), state);
        String fragment = state.payload().path("prompt").path("persona").asText();
        assertTrue(fragment.contains("outer: calm"));
        assertTrue(fragment.contains("inner: guarded"));
        assertTrue(fragment.contains("origin: a small town"));
        assertTrue(fragment.contains("speech: short sentences"));
        assertTrue(fragment.contains("triggers: silence"));
        assertTrue(fragment.contains("redlines: no insults"));
        assertEquals(0, state.ui().size());
        assertTrue(state.hint().contains("模型"));
        assertFalse(state.hint().contains(fragment));
        assertTrue(state.dirty());
    }

    @Test
    void personaAssembleRejectsMissingFieldsWithoutWritingPrompt() throws Exception {
        WorkingState state = new WorkingState(mapper, snapshot("""
                {"persona":{"outer":"calm","origin":"x","speech":"y"}}
                """));
        ToolFailure failure = assertThrows(ToolFailure.class,
                () -> catalog.execute("persona.assemble", mapper.createObjectNode(), state));
        assertEquals("PRECONDITION_FAILED", failure.code());
        assertFalse(state.payload().has("prompt"));
        assertEquals(0, state.ui().size());
        assertFalse(state.dirty());
    }

    @Test
    void chatGroupSendPersistsSpeakerAndBubble() {
        WorkingState state = new WorkingState(mapper, snapshot("{}"));
        catalog.execute("chat.group.send", mapper.createObjectNode().put("content", "hello").put("speaker", "A"), state);
        JsonNode ui = state.ui().get(0);
        assertEquals("chat.messages", ui.path("target").asText());
        assertEquals("append", ui.path("op").asText());
        assertEquals("chat.bubble", ui.path("node").path("type").asText());
        assertEquals("hello", ui.path("node").path("props").path("text").asText());
        assertEquals("npc", ui.path("node").path("props").path("role").asText());
        assertEquals("A", ui.path("node").path("props").path("speaker").asText());
        JsonNode line = state.payload().path("groupMessages").get(0);
        assertEquals("group", line.path("channel").asText());
        assertEquals("A", line.path("speaker").asText());
        assertEquals("hello", line.path("text").asText());
    }

    @Test
    void chatGroupSendRejectsBlankSpeakerWithoutMutation() {
        WorkingState state = new WorkingState(mapper, snapshot("{}"));
        ToolFailure failure = assertThrows(ToolFailure.class,
                () -> catalog.execute("chat.group.send", mapper.createObjectNode().put("content", "hi").put("speaker", " "), state));
        assertEquals("PRECONDITION_FAILED", failure.code());
        assertFalse(state.payload().has("groupMessages"));
        assertEquals(0, state.ui().size());
        assertFalse(state.dirty());
    }

    @Test
    void redpacketSendRefusesWhenWalletCannotCover() throws Exception {
        WorkingState state = new WorkingState(mapper, snapshot("{\"wallet\":10,\"redpacketCount\":2}"));
        String before = state.payload().toString();
        ToolFailure over = assertThrows(ToolFailure.class,
                () -> catalog.execute("redpacket.send", mapper.readTree("{\"amount\":20}"), state));
        assertEquals("PRECONDITION_FAILED", over.code());
        assertEquals(before, state.payload().toString());
        assertEquals(0, state.ui().size());
        assertFalse(state.dirty());

        WorkingState emptyWallet = new WorkingState(mapper, snapshot("{}"));
        ToolFailure missingWallet = assertThrows(ToolFailure.class,
                () -> catalog.execute("redpacket.send", mapper.readTree("{\"amount\":1}"), emptyWallet));
        assertEquals("PRECONDITION_FAILED", missingWallet.code());
        assertFalse(emptyWallet.payload().has("wallet"));
        assertFalse(emptyWallet.payload().has("redpacketCount"));
        assertEquals(0, emptyWallet.ui().size());

        WorkingState zeroAmount = new WorkingState(mapper, snapshot("{\"wallet\":100}"));
        ToolFailure missingAmount = assertThrows(ToolFailure.class,
                () -> catalog.execute("redpacket.send", mapper.readTree("{\"note\":\"x\"}"), zeroAmount));
        assertEquals("PRECONDITION_FAILED", missingAmount.code());
        assertEquals(100.0, zeroAmount.payload().path("wallet").asDouble());
        assertEquals(0, zeroAmount.ui().size());
    }

    @Test
    void sharedL2ToolsAreStoryAgnostic() throws Exception {
        WorkingState state = new WorkingState(mapper, snapshot("""
                {"wallet":100,"persona":{"outer":"o","inner":"i","origin":"g","speech":"s"}}
                """));
        catalog.execute("persona.assemble", mapper.createObjectNode(), state);
        catalog.execute("chat.send", mapper.createObjectNode().put("content", "line"), state);
        catalog.execute("chat.group.send", mapper.createObjectNode().put("content", "group").put("speaker", "B"), state);
        catalog.execute("redpacket.send", mapper.readTree("{\"amount\":20}"), state);
        assertEquals(80.0, state.payload().path("wallet").asDouble());
        assertTrue(state.payload().path("prompt").path("persona").isTextual());
        assertEquals("direct", state.payload().path("messages").get(0).path("channel").asText());
        assertEquals("group", state.payload().path("groupMessages").get(0).path("channel").asText());
        assertEquals("chat.bubble", state.ui().get(0).path("node").path("type").asText());
        assertEquals("chat.bubble", state.ui().get(1).path("node").path("type").asText());
        assertEquals("redpacket-card", state.ui().get(2).path("node").path("type").asText());
        assertEquals("wallet-balance", state.ui().get(3).path("node").path("type").asText());
        assertEquals(0, state.ui().size() - 4);
    }

    private static SessionStateSnapshot snapshot(String payload) {
        return new SessionStateSnapshot(new SessionRef("s", "w"), "INTRO", "1.0", payload);
    }
}
