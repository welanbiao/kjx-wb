package com.koujunxin.tool.builtin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.koujunxin.runtime.api.dto.SessionRef;
import com.koujunxin.runtime.api.dto.SessionStateSnapshot;
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
    }

    private static SessionStateSnapshot snapshot(String payload) {
        return new SessionStateSnapshot(new SessionRef("s", "w"), "INTRO", "1.0", payload);
    }
}
