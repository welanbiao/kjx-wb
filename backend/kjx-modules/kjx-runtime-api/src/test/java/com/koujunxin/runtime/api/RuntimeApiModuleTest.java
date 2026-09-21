package com.koujunxin.runtime.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.koujunxin.runtime.api.dto.SessionRef;
import com.koujunxin.runtime.api.dto.SessionStateSnapshot;
import com.koujunxin.runtime.api.dto.StateMutation;
import com.koujunxin.runtime.api.dto.ToolExecutionCommand;
import com.koujunxin.runtime.api.dto.ToolResult;
import com.koujunxin.runtime.api.error.EntitlementRequiredException;
import com.koujunxin.runtime.api.error.SessionNotOwnedException;
import com.koujunxin.runtime.api.port.EntitlementAccessPort;
import com.koujunxin.runtime.api.port.SessionAuthorizationPort;
import com.koujunxin.runtime.api.port.SessionStateCommandPort;
import com.koujunxin.runtime.api.port.SessionStateQueryPort;
import com.koujunxin.runtime.api.port.ToolExecutionPort;

class RuntimeApiModuleTest {

    @Test
    void moduleNameIsStable() {
        assertEquals("kjx-runtime-api", RuntimeApiModule.NAME);
    }

    @Test
    void portsAreInterfacesWithoutImplementationBinding() {
        assertTrue(SessionStateQueryPort.class.isInterface());
        assertTrue(SessionStateCommandPort.class.isInterface());
        assertTrue(SessionAuthorizationPort.class.isInterface());
        assertTrue(EntitlementAccessPort.class.isInterface());
        assertTrue(ToolExecutionPort.class.isInterface());
    }

    @Test
    void dtosAreImmutableRecordsAndImplementationNeutral() throws Exception {
        assertTrue(SessionRef.class.isRecord());
        assertTrue(SessionStateSnapshot.class.isRecord());
        assertTrue(StateMutation.class.isRecord());
        assertTrue(ToolExecutionCommand.class.isRecord());
        assertTrue(ToolResult.class.isRecord());

        for (Class<?> type : Set.of(
                SessionRef.class,
                SessionStateSnapshot.class,
                StateMutation.class,
                ToolExecutionCommand.class,
                ToolResult.class)) {
            assertNeutralComponents(type);
            for (Field field : type.getDeclaredFields()) {
                if (!field.isSynthetic()) {
                    assertTrue(Modifier.isFinal(field.getModifiers()), field + " must be final");
                }
            }
        }
    }

    @Test
    void dtosStayImplementationNeutral() {
        SessionRef session = new SessionRef("sess-1", "work-demo");
        SessionStateSnapshot snapshot = new SessionStateSnapshot(session, "INTRO", "1", "{}");
        StateMutation mutation = new StateMutation("ADVANCE", "1", "{}");
        ToolExecutionCommand command = new ToolExecutionCommand(
                "builtin.echo", "1.0.0", session, 1L, "work-demo", "key-1", "{}");
        ToolResult result = new ToolResult(true, "OK", "done", "{}");

        assertEquals("sess-1", snapshot.session().sessionId());
        assertEquals("ADVANCE", mutation.type());
        assertEquals("builtin.echo", command.toolCode());
        assertTrue(result.success());
        assertEquals("OK", ToolResult.ok("{}").code());
        assertEquals("DENIED", ToolResult.fail("DENIED", "no").code());
    }

    @Test
    void dtosRejectBlankRequiredFields() {
        assertThrows(IllegalArgumentException.class, () -> new SessionRef(" ", "work"));
        assertThrows(IllegalArgumentException.class, () -> new SessionRef("sess", ""));
        assertThrows(IllegalArgumentException.class, () -> new StateMutation("", "1", "{}"));
        assertThrows(IllegalArgumentException.class, () -> new ToolExecutionCommand(
                "echo", "1", new SessionRef("s", "w"), 0L, "w", "k", "{}"));
    }

    @Test
    void portStubsDoNotDependOnRuntimeOrToolTypes() {
        SessionStateQueryPort query = session -> new SessionStateSnapshot(session, "S", "1.0", "{}");
        SessionStateCommandPort command = (session, mutation) -> query.load(session);
        SessionAuthorizationPort auth = (accountId, session) -> {
            if (accountId <= 0) {
                throw new SessionNotOwnedException(accountId, session.sessionId());
            }
        };
        EntitlementAccessPort entitlement = (accountId, workCode) -> {
            throw new EntitlementRequiredException(accountId, workCode);
        };
        ToolExecutionPort tools = cmd -> ToolResult.ok(cmd.argumentsJson());

        SessionRef session = new SessionRef("sess-1", "work-demo");
        assertEquals("S", query.load(session).stage());
        assertEquals("S", command.apply(session, new StateMutation("ADVANCE", "1.0", "{}")).stage());
        auth.requireOwner(9L, session);
        assertThrows(EntitlementRequiredException.class, () -> entitlement.requireEntitlement(9L, "work-demo"));
        assertTrue(tools.execute(new ToolExecutionCommand(
                "builtin.echo", "1.0.0", session, 9L, "work-demo", "idem-1", "{\"x\":1}")).success());
    }

    @Test
    void portExceptionsStayNeutral() {
        SessionNotOwnedException owned = new SessionNotOwnedException(3L, "sess-1");
        EntitlementRequiredException missing = new EntitlementRequiredException(3L, "work-demo");
        assertEquals("SESSION_NOT_OWNED", owned.code());
        assertEquals("ENTITLEMENT_REQUIRED", missing.code());
        assertInstanceOf(RuntimeException.class, owned);
    }

    private static void assertNeutralComponents(Class<?> type) {
        for (RecordComponent component : type.getRecordComponents()) {
            Class<?> componentType = component.getType();
            String name = componentType.getName();
            assertTrue(
                    componentType.isPrimitive()
                            || name.startsWith("java.")
                            || name.startsWith("com.koujunxin.runtime.api."),
                    type.getSimpleName() + "." + component.getName() + " leaks " + name);
        }
    }
}
