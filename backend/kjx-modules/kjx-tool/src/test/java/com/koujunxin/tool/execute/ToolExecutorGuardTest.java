package com.koujunxin.tool.execute;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.koujunxin.runtime.api.dto.SessionRef;
import com.koujunxin.runtime.api.dto.ToolExecutionCommand;
import com.koujunxin.runtime.api.dto.ToolResult;
import com.koujunxin.runtime.api.error.EntitlementRequiredException;
import com.koujunxin.runtime.api.error.SessionNotOwnedException;
import com.koujunxin.tool.api.RateLimitSpec;
import com.koujunxin.tool.api.ToolFailure;
import com.koujunxin.tool.registry.ToolRegistration;
import com.koujunxin.tool.support.ToolHarness;

class ToolExecutorGuardTest {

    @Test
    void expiredMemberWithEntitlementCanCall() {
        ToolHarness harness = ready("time.get");
        ToolResult result = harness.executor().execute(command("time.get", "k1", "{}"));
        assertTrue(result.success());
        assertEquals("OK", result.code());
    }

    @Test
    void missingEntitlementIsRejected() {
        ToolHarness harness = new ToolHarness();
        harness.registerBuiltin("time.get", emptyObjectSchema(), RateLimitSpec.unlimited(), 2000, 0);
        harness.own("sess-1", 9L);
        harness.snapshot("sess-1", "work-a", "INTRO", "{}");
        assertThrows(EntitlementRequiredException.class,
                () -> harness.executor().execute(command("time.get", "k1", "{}")));
    }

    @Test
    void otherAccountSessionIsRejected() {
        ToolHarness harness = new ToolHarness();
        harness.registerBuiltin("time.get", emptyObjectSchema(), RateLimitSpec.unlimited(), 2000, 0);
        harness.grant(9L, "work-a");
        harness.own("sess-1", 8L);
        harness.snapshot("sess-1", "work-a", "INTRO", "{}");
        assertThrows(SessionNotOwnedException.class,
                () -> harness.executor().execute(command("time.get", "k1", "{}")));
    }

    @Test
    void invalidArgumentsAreRejected() {
        ToolHarness harness = ready("wallet.adjust");
        harness.registerBuiltin(
                "wallet.adjust",
                "{\"type\":\"object\",\"properties\":{\"delta\":{\"type\":\"number\"}},\"required\":[\"delta\"],\"additionalProperties\":false}",
                RateLimitSpec.unlimited(),
                2000,
                0);
        ToolResult result = harness.executor().execute(command("wallet.adjust", "k2", "{}"));
        assertEquals("INVALID_ARGUMENTS", result.code());
    }

    @Test
    void sameCallShortCircuits() {
        ToolHarness harness = ready("time.get");
        ToolExecutor executor = harness.executor();
        ToolResult first = executor.execute(command("time.get", "same", "{}"));
        ToolResult second = executor.execute(command("time.get", "same", "{}"));
        assertTrue(first.success());
        assertEquals(first.payloadJson(), second.payloadJson());
    }

    @Test
    void sameKeyDifferentCanonicalArgsConflict() {
        ToolHarness harness = ready("moments.post");
        harness.registerBuiltin(
                "moments.post",
                "{\"type\":\"object\",\"properties\":{\"content\":{\"type\":\"string\"}},\"required\":[\"content\"],\"additionalProperties\":false}",
                RateLimitSpec.unlimited(),
                2000,
                0);
        ToolExecutor executor = harness.executor();
        assertTrue(executor.execute(command("moments.post", "m1", "{\"content\":\"a\"}")).success());
        ToolResult conflict = executor.execute(command("moments.post", "m1", "{\"content\":\"b\"}"));
        assertEquals("IDEMPOTENCY_CONFLICT", conflict.code());
    }

    @Test
    void rateLimitRejectsAfterQuota() {
        ToolHarness harness = ready("time.get");
        harness.registerBuiltin("time.get", emptyObjectSchema(), new RateLimitSpec(1, null, null), 2000, 0);
        ToolExecutor executor = harness.executor();
        assertTrue(executor.execute(command("time.get", "r1", "{}")).success());
        assertEquals("RATE_LIMITED", executor.execute(command("time.get", "r2", "{}")).code());
    }

    @Test
    void composeAppliesSessionOnceAndDoesNotOpenPerStepTransaction() throws Exception {
        ToolHarness harness = ready("metric.adjust");
        ObjectNode impl = (ObjectNode) harness.mapper.readTree("""
                {"kind":"compose","steps":[
                  {"op":"state.set","path":"a","value":1},
                  {"op":"state.set","path":"b","value":2},
                  {"op":"state.set","path":"c","value":3},
                  {"op":"ui.render","target":"toast","uiOp":"append","node":{"type":"toast","props":{"text":"ok"}}}
                ]}
                """);
        harness.catalog.register(new ToolRegistration(
                "metric.adjust",
                "1.0.0",
                "custom",
                "指标",
                "指标",
                harness.mapper.readTree("{\"type\":\"object\",\"properties\":{\"delta\":{\"type\":\"number\"}},\"required\":[\"delta\"],\"additionalProperties\":false}"),
                harness.mapper.createObjectNode(),
                impl,
                RateLimitSpec.unlimited(),
                2000,
                0));
        harness.catalog.bindWork("work-a", "metric.adjust", "1.0.0", true);
        ToolResult result = harness.executor().execute(command("metric.adjust", "c1", "{\"delta\":2}"));
        assertTrue(result.success());
        assertEquals(1, harness.state.applies.get());
        assertEquals(2, harness.unitOfWork.count.get());
    }

    @Test
    void retryableBuiltinRetriesThenSucceeds() {
        ToolHarness harness = ready("test.retry");
        AtomicInteger attempts = new AtomicInteger();
        harness.extraBuiltins.put("test.retry", (args, state) -> {
            if (attempts.incrementAndGet() < 3) {
                throw ToolFailure.retryable("UPSTREAM", "try again");
            }
            state.hint("recovered");
        });
        harness.registerBuiltin("test.retry", emptyObjectSchema(), RateLimitSpec.unlimited(), 2000, 2);
        ToolResult result = harness.executor().execute(command("test.retry", "t1", "{}"));
        assertTrue(result.success());
        assertEquals(3, attempts.get());
    }

    @Test
    void timeoutReturnsUnavailable() {
        ToolHarness harness = ready("test.hang");
        harness.extraBuiltins.put("test.hang", (args, state) -> {
            try {
                Thread.sleep(5_000);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw ToolFailure.of("TOOL_TIMEOUT", "信息不可用");
            }
        });
        harness.registerBuiltin("test.hang", emptyObjectSchema(), RateLimitSpec.unlimited(), 80, 0);
        ToolResult result = harness.executor().execute(command("test.hang", "h1", "{}"));
        assertEquals("TOOL_TIMEOUT", result.code());
        assertTrue(result.message().contains("信息不可用"));
    }

    @Test
    void scriptImplementationIsRejected() {
        ToolHarness harness = new ToolHarness();
        ObjectNode impl = harness.mapper.createObjectNode();
        impl.put("kind", "script");
        impl.put("body", "return 1");
        assertThrows(ToolFailure.class, () -> harness.catalog.register(new ToolRegistration(
                "evil.run",
                "1.0.0",
                "custom",
                "evil",
                "evil",
                harness.mapper.createObjectNode().put("type", "object"),
                harness.mapper.createObjectNode(),
                impl,
                RateLimitSpec.unlimited(),
                1000,
                0)));
    }

    @Test
    void executorSourceDoesNotTakeMemberStatus() throws Exception {
        var ctor = ToolExecutor.class.getConstructors()[0];
        for (var param : ctor.getParameters()) {
            assertTrue(!"memberStatus".equals(param.getName()) && !"status".equals(param.getName()));
        }
    }

    private static ToolHarness ready(String ignored) {
        ToolHarness harness = new ToolHarness();
        harness.registerBuiltin("time.get", emptyObjectSchema(), RateLimitSpec.unlimited(), 2000, 0);
        harness.grant(9L, "work-a");
        harness.own("sess-1", 9L);
        harness.snapshot("sess-1", "work-a", "INTRO", "{}");
        return harness;
    }

    private static ToolExecutionCommand command(String tool, String key, String args) {
        return new ToolExecutionCommand(tool, "1.0.0", new SessionRef("sess-1", "work-a"), 9L, "work-a", key, args);
    }

    private static String emptyObjectSchema() {
        return "{\"type\":\"object\",\"properties\":{},\"additionalProperties\":false}";
    }
}
