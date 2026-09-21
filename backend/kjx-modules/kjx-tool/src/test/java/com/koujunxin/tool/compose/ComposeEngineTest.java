package com.koujunxin.tool.compose;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.koujunxin.runtime.api.dto.SessionRef;
import com.koujunxin.runtime.api.dto.SessionStateSnapshot;
import com.koujunxin.tool.api.ToolFailure;
import com.koujunxin.tool.builtin.BuiltinCatalog;
import com.koujunxin.tool.state.WorkingState;
import com.koujunxin.tool.support.ToolHarness;

class ComposeEngineTest {

    private final ToolHarness harness = new ToolHarness();
    private final ObjectMapper mapper = harness.mapper;
    private final ComposeEngine engine = new ComposeEngine(
            mapper,
            new BuiltinCatalog(mapper, Clock.systemUTC()),
            harness.platformUi(),
            Clock.systemUTC());

    @Test
    void sequentialStepsShareOneWorkingState() throws Exception {
        JsonNode impl = mapper.readTree("""
                {"kind":"compose","steps":[
                  {"op":"state.set","path":"metrics.a","value":1},
                  {"op":"state.set","path":"metrics.b","value":2},
                  {"op":"assert","cond":{"path":"$.args.delta","lte":15},"message":"过大"},
                  {"op":"ui.render","target":"toast","uiOp":"append","node":{"type":"toast","props":{"text":"ok"}}},
                  {"op":"return","value":{"ok":true}}
                ]}
                """);
        WorkingState state = new WorkingState(mapper, snapshot());
        engine.run(impl, mapper.readTree("{\"delta\":3}"), state, 0);
        assertEquals(1, state.payload().path("metrics").path("a").asInt());
        assertEquals(2, state.payload().path("metrics").path("b").asInt());
        assertEquals(1, state.ui().size());
        assertTrue(state.returned());
    }

    @Test
    void scriptLikeOpIsRejected() throws Exception {
        JsonNode impl = mapper.readTree("""
                {"kind":"compose","steps":[{"op":"eval","expr":"1+1"}]}
                """);
        WorkingState state = new WorkingState(mapper, snapshot());
        ToolFailure failure = assertThrows(ToolFailure.class, () -> engine.run(impl, mapper.createObjectNode(), state, 0));
        assertEquals("TOOL_SCRIPT_FORBIDDEN", failure.code());
    }

    @Test
    void unknownUiNodeInComposeIsRejected() throws Exception {
        JsonNode impl = mapper.readTree("""
                {"kind":"compose","steps":[
                  {"op":"ui.render","target":"x","uiOp":"append","node":{"type":"iframe","props":{"text":"x"}}}
                ]}
                """);
        WorkingState state = new WorkingState(mapper, snapshot());
        ToolFailure failure = assertThrows(ToolFailure.class, () -> engine.run(impl, mapper.createObjectNode(), state, 0));
        assertEquals("UI_NODE_NOT_ALLOWED", failure.code());
    }

    private static SessionStateSnapshot snapshot() {
        return new SessionStateSnapshot(new SessionRef("s1", "work-a"), "INTRO", "1.0", "{}");
    }
}
