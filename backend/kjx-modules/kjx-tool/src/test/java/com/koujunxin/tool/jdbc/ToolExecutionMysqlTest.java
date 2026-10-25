package com.koujunxin.tool.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.koujunxin.runtime.api.dto.SessionRef;
import com.koujunxin.runtime.api.dto.ToolExecutionCommand;
import com.koujunxin.runtime.api.dto.ToolResult;
import com.koujunxin.runtime.api.error.EntitlementRequiredException;
import com.koujunxin.runtime.api.error.SessionNotOwnedException;
import com.koujunxin.tool.api.RateLimitSpec;
import com.koujunxin.tool.api.ToolFailure;
import com.koujunxin.tool.execute.ToolExecutor;
import com.koujunxin.tool.registry.ToolRegistration;
import com.koujunxin.tool.support.ToolHarness;

class ToolExecutionMysqlTest extends MysqlToolSupport {

    @BeforeEach
    void clean() {
        resetRuntimeTables();
    }

    @Test
    void flywayVersionsAndTablesExist() {
        assertEquals(10, jdbc().queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version BETWEEN '620' AND '629'",
                Integer.class));
        assertEquals(1, jdbc().queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'kjx_tool'",
                Integer.class));
        assertEquals(1, jdbc().queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'kjx_ui_node_type'",
                Integer.class));
        assertTrue(jdbc().queryForObject("SELECT COUNT(*) FROM kjx_tool WHERE code = 'time.get'", Integer.class) > 0);
        assertTrue(jdbc().queryForObject("SELECT COUNT(*) FROM kjx_tool WHERE code = 'metric.adjust'", Integer.class) > 0);
        assertTrue(jdbc().queryForObject("SELECT COUNT(*) FROM kjx_tool WHERE code = 'persona.assemble'", Integer.class) > 0);
        assertTrue(jdbc().queryForObject("SELECT COUNT(*) FROM kjx_tool WHERE code = 'chat.group.send'", Integer.class) > 0);
        assertEquals("string", jdbc().queryForObject(
                "SELECT props_schema ->> '$.properties.speaker.type' FROM kjx_ui_node_type WHERE type = 'chat.bubble'",
                String.class));
    }

    @Test
    void expiredEntitlementHolderCanCallBuiltin() {
        ToolHarness.GrantingEntitlement entitlements = entitlements();
        entitlements.grant(9L, "work-a");
        ToolHarness.OwnerAuth owners = owners();
        owners.own("sess-1", 9L);
        ToolHarness.MemorySessionState state = sessionState();
        state.put(new com.koujunxin.runtime.api.dto.SessionStateSnapshot(
                new SessionRef("sess-1", "work-a"), "INTRO", "1.0", "{}"));
        ToolExecutor executor = executor(entitlements, owners, state, state);
        ToolResult result = executor.execute(cmd("time.get", "idem-1", "{}"));
        assertTrue(result.success());
        assertEquals(1, jdbc().queryForObject("SELECT COUNT(*) FROM kjx_tool_call WHERE idempotency_key = 'idem-1'", Integer.class));
    }

    @Test
    void missingEntitlementAndForeignSessionAreRejected() {
        ToolHarness.GrantingEntitlement entitlements = entitlements();
        ToolHarness.OwnerAuth owners = owners();
        owners.own("sess-1", 8L);
        ToolHarness.MemorySessionState state = sessionState();
        ToolExecutor executor = executor(entitlements, owners, state, state);
        assertThrows(EntitlementRequiredException.class, () -> executor.execute(cmd("time.get", "e1", "{}")));
        entitlements.grant(9L, "work-a");
        assertThrows(SessionNotOwnedException.class, () -> executor.execute(cmd("time.get", "e2", "{}")));
    }

    @Test
    void mysqlIdempotencyShortCircuitsAndPersistsOnce() throws Exception {
        ToolHarness.GrantingEntitlement entitlements = entitlements();
        entitlements.grant(9L, "work-a");
        ToolHarness.OwnerAuth owners = owners();
        owners.own("sess-1", 9L);
        ToolHarness.MemorySessionState state = sessionState();
        ToolExecutor executor = executor(entitlements, owners, state, state);
        ToolResult first = executor.execute(cmd("chat.send", "chat-1", "{\"content\":\"hello\"}"));
        ToolResult second = executor.execute(cmd("chat.send", "chat-1", "{\"content\":\"hello\"}"));
        assertTrue(first.success());
        assertTrue(second.success());
        assertEquals(first.code(), second.code());
        assertEquals(mapper().readTree(first.payloadJson()), mapper().readTree(second.payloadJson()));
        assertEquals(1, jdbc().queryForObject("SELECT COUNT(*) FROM kjx_tool_call WHERE idempotency_key = 'chat-1'", Integer.class));
        ToolResult conflict = executor.execute(cmd("chat.send", "chat-1", "{\"content\":\"other\"}"));
        assertEquals("IDEMPOTENCY_CONFLICT", conflict.code());
    }

    @Test
    void composeSampleRequiresWorkBindingAndAppliesOnce() {
        ToolHarness.GrantingEntitlement entitlements = entitlements();
        entitlements.grant(9L, "work-a");
        ToolHarness.OwnerAuth owners = owners();
        owners.own("sess-1", 9L);
        ToolHarness.MemorySessionState state = sessionState();
        ToolExecutor executor = executor(entitlements, owners, state, state);
        ToolResult disabled = executor.execute(cmd("metric.adjust", "m0", "{\"delta\":2}"));
        assertEquals("TOOL_DISABLED", disabled.code());

        new JdbcToolRegistry(jdbc(), mapper()).bindWork("work-a", "metric.adjust", "1.0.0", true);
        ToolResult ok = executor.execute(cmd("metric.adjust", "m1", "{\"delta\":2}"));
        assertTrue(ok.success());
        assertEquals(1, state.applies.get());
        ToolResult tooBig = executor.execute(cmd("metric.adjust", "m2", "{\"delta\":20}"));
        assertEquals("PRECONDITION_FAILED", tooBig.code());
    }

    @Test
    void sharedL2BuiltinsPersistStateAndWhitelistedUi() throws Exception {
        ToolHarness.GrantingEntitlement entitlements = entitlements();
        entitlements.grant(9L, "work-a");
        ToolHarness.OwnerAuth owners = owners();
        owners.own("sess-1", 9L);
        ToolHarness.MemorySessionState state = sessionState();
        state.put(new com.koujunxin.runtime.api.dto.SessionStateSnapshot(
                new SessionRef("sess-1", "work-a"),
                "INTRO",
                "1.0",
                "{\"wallet\":100,\"persona\":{\"outer\":\"o\",\"inner\":\"i\",\"origin\":\"g\",\"speech\":\"s\"}}"));
        ToolExecutor executor = executor(entitlements, owners, state, state);

        ToolResult persona = executor.execute(cmd("persona.assemble", "p1", "{}"));
        assertTrue(persona.success());
        JsonNode personaPayload = mapper().readTree(persona.payloadJson());
        assertTrue(personaPayload.path("state").path("prompt").path("persona").isTextual());
        assertEquals(0, personaPayload.path("ui").size());

        ToolResult chat = executor.execute(cmd("chat.send", "c1", "{\"content\":\"hi\"}"));
        assertTrue(chat.success());
        JsonNode chatPayload = mapper().readTree(chat.payloadJson());
        assertEquals("chat.bubble", chatPayload.path("ui").get(0).path("node").path("type").asText());
        assertEquals("direct", chatPayload.path("state").path("messages").get(0).path("channel").asText());

        ToolResult group = executor.execute(cmd("chat.group.send", "g1", "{\"content\":\"hey\",\"speaker\":\"A\"}"));
        assertTrue(group.success());
        JsonNode groupPayload = mapper().readTree(group.payloadJson());
        assertEquals("chat.bubble", groupPayload.path("ui").get(0).path("node").path("type").asText());
        assertEquals("A", groupPayload.path("ui").get(0).path("node").path("props").path("speaker").asText());
        assertEquals("group", groupPayload.path("state").path("groupMessages").get(0).path("channel").asText());

        ToolResult packet = executor.execute(cmd("redpacket.send", "r1", "{\"amount\":20,\"note\":\"cake\"}"));
        assertTrue(packet.success());
        JsonNode packetPayload = mapper().readTree(packet.payloadJson());
        assertEquals("redpacket-card", packetPayload.path("ui").get(0).path("node").path("type").asText());
        assertEquals(80.0, packetPayload.path("state").path("wallet").asDouble());

        Thread.sleep(1100);
        ToolResult refused = executor.execute(cmd("redpacket.send", "r2", "{\"amount\":1000}"));
        assertEquals("PRECONDITION_FAILED", refused.code());
        assertEquals(80.0, mapper().readTree(state.load(new SessionRef("sess-1", "work-a")).payloadJson()).path("wallet").asDouble());
    }

    @Test
    void scriptCannotBeRegistered() {
        JdbcToolRegistry registry = new JdbcToolRegistry(jdbc(), mapper());
        ObjectNode impl = mapper().createObjectNode();
        impl.put("kind", "script");
        impl.put("body", "return 1");
        ToolFailure failure = assertThrows(ToolFailure.class, () -> registry.register(new ToolRegistration(
                "evil.run",
                "1.0.0",
                "custom",
                "evil",
                "evil",
                mapper().createObjectNode().put("type", "object"),
                mapper().createObjectNode(),
                impl,
                RateLimitSpec.unlimited(),
                1000,
                0)));
        assertEquals("TOOL_SCRIPT_FORBIDDEN", failure.code());
    }

    @Test
    void unknownUiNodeFromRegisteredComposeIsRejected() throws Exception {
        JdbcToolRegistry registry = new JdbcToolRegistry(jdbc(), mapper());
        registry.register(new ToolRegistration(
                "bad.ui",
                "1.0.0",
                "custom",
                "bad",
                "bad",
                mapper().readTree("{\"type\":\"object\",\"properties\":{},\"additionalProperties\":false}"),
                mapper().createObjectNode(),
                mapper().readTree("""
                        {"kind":"compose","steps":[
                          {"op":"ui.render","target":"x","uiOp":"append","node":{"type":"iframe","props":{"text":"x"}}}
                        ]}
                        """),
                RateLimitSpec.unlimited(),
                2000,
                0));
        registry.bindWork("work-a", "bad.ui", "1.0.0", true);
        ToolHarness.GrantingEntitlement entitlements = entitlements();
        entitlements.grant(9L, "work-a");
        ToolHarness.OwnerAuth owners = owners();
        owners.own("sess-1", 9L);
        ToolExecutor executor = executor(entitlements, owners, sessionState(), sessionState());
        ToolResult result = executor.execute(cmd("bad.ui", "u1", "{}"));
        assertEquals("UI_NODE_NOT_ALLOWED", result.code());
    }

    private static ToolExecutionCommand cmd(String tool, String key, String args) {
        return new ToolExecutionCommand(tool, "1.0.0", new SessionRef("sess-1", "work-a"), 9L, "work-a", key, args);
    }
}
