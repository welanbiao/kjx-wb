package com.koujunxin.runtime.sandbox;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import com.koujunxin.runtime.api.RuntimeFailure;
import com.koujunxin.runtime.session.InMemoryRuntimeStore;

class SandboxGuardTest {

    @Test
    void nonceOriginAndMethodMustMatch() {
        InMemoryRuntimeStore store = new InMemoryRuntimeStore();
        SandboxGuard guard = new SandboxGuard(store, Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC), Duration.ofHours(1));
        SandboxGuard.IssuedTicket ticket = guard.issue("sid", 8L, "phone-demo", "https://demo.game.example");
        guard.require(ticket.nonce(), "https://demo.game.example", "state.save", 8L, "sid", "phone-demo");
        assertThrows(RuntimeFailure.class,
                () -> guard.require(ticket.nonce(), "https://evil.example", "state.save", 8L, "sid", "phone-demo"));
        assertThrows(RuntimeFailure.class,
                () -> guard.require("wrong", "https://demo.game.example", "state.save", 8L, "sid", "phone-demo"));
        assertThrows(RuntimeFailure.class,
                () -> guard.require(ticket.nonce(), "https://demo.game.example", "window.eval", 8L, "sid", "phone-demo"));
        assertThrows(RuntimeFailure.class,
                () -> guard.require(ticket.nonce(), "https://demo.game.example", "state.save", 9L, "sid", "phone-demo"));
        assertThrows(RuntimeFailure.class,
                () -> guard.require(ticket.nonce(), "https://demo.game.example", "state.save", 8L, "other", "phone-demo"));
        assertThrows(RuntimeFailure.class,
                () -> guard.require(ticket.nonce(), "https://demo.game.example", "state.save", 8L, "sid", "other-work"));
    }

    @Test
    void iframeMustNotReceiveSameOriginSandbox() {
        assertTrue(SandboxGuard.forbiddenIframeFlag("allow-scripts allow-forms allow-same-origin"));
        assertFalse(SandboxGuard.forbiddenIframeFlag(SandboxMethods.IFRAME_SANDBOX));
        assertEquals("allow-scripts allow-forms", SandboxMethods.IFRAME_SANDBOX);
    }

    @Test
    void sanitizerStripsTokenFields() {
        String cleaned = HostSanitizer.sanitizeJson("{\"text\":\"ok\",\"token\":\"secret\",\"Authorization\":\"Bearer x\"}");
        assertFalse(cleaned.toLowerCase().contains("token"));
        assertFalse(cleaned.toLowerCase().contains("bearer"));
        assertTrue(cleaned.contains("ok"));
    }
}
