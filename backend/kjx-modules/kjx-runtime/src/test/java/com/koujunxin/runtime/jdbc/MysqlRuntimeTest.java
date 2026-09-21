package com.koujunxin.runtime.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.koujunxin.runtime.api.dto.StateMutation;
import com.koujunxin.runtime.api.error.EntitlementRequiredException;
import com.koujunxin.runtime.api.port.EntitlementAccessPort;
import com.koujunxin.runtime.api.dto.ToolResult;
import com.koujunxin.runtime.ledger.SaveSigner;
import com.koujunxin.runtime.sandbox.SandboxGuard;
import com.koujunxin.runtime.session.JdbcRuntimeStore;
import com.koujunxin.runtime.session.RuntimeApplicationService;
import com.koujunxin.runtime.session.SessionStateService;
import com.koujunxin.runtime.support.FakeWorkRuntimePort;

class MysqlRuntimeTest extends MysqlRuntimeSupport {

    private RuntimeApplicationService app;

    @BeforeEach
    void setup() {
        resetTables();
        JdbcRuntimeStore store = new JdbcRuntimeStore(jdbc());
        FakeWorkRuntimePort works = new FakeWorkRuntimePort().registerFormA("phone-demo");
        EntitlementAccessPort entitlements = (accountId, workCode) -> {
            if (accountId != 11L || !"phone-demo".equals(workCode)) {
                throw new EntitlementRequiredException(accountId, workCode);
            }
        };
        SessionStateService sessions = new SessionStateService(store, works);
        app = new RuntimeApplicationService(
                store,
                sessions,
                entitlements,
                works,
                new SaveSigner("mysql-runtime-secret"),
                new SandboxGuard(store, Clock.systemUTC(), Duration.ofHours(1)),
                command -> ToolResult.ok("{\"mysql\":true}"),
                null,
                tx());
    }

    @Test
    void persistsSessionSaveAndRejectsForeignAccount() {
        Map<String, Object> created = app.createSession(11L, "phone-demo");
        String sessionId = (String) created.get("sessionId");
        app.apply(11L, sessionId, "phone-demo", new StateMutation("SET_STAGE", "1", "{\"stage\":\"2\"}"));
        Map<String, Object> saved = app.save(11L, sessionId, "phone-demo", "auto", null);
        assertEquals("auto", saved.get("slot"));
        assertEquals(1, jdbc().queryForObject("SELECT COUNT(*) FROM kjx_runtime_session", Integer.class));
        assertEquals(1, jdbc().queryForObject("SELECT COUNT(*) FROM kjx_runtime_save", Integer.class));
        Map<String, Object> stage = app.stage(11L, sessionId, "phone-demo");
        assertTrue(String.valueOf(stage.get("nodesJson")).contains("secret-ending"));
        assertThrows(EntitlementRequiredException.class, () -> app.createSession(99L, "phone-demo"));
    }
}
