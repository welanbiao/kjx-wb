package com.koujunxin.runtime.sandbox;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

import com.koujunxin.runtime.api.RuntimeFailure;
import com.koujunxin.runtime.ledger.CanonicalJson;
import com.koujunxin.runtime.session.RuntimeStore;

/**
 * nonce + origin + 方法白名单。票据只绑定会话，不携带登录 token。
 */
public final class SandboxGuard {

    public static final String OPAQUE_ORIGIN = "null";

    private final RuntimeStore store;
    private final Clock clock;
    private final Duration ttl;

    public SandboxGuard(RuntimeStore store, Clock clock, Duration ttl) {
        this.store = store;
        this.clock = clock;
        this.ttl = ttl == null ? Duration.ofHours(2) : ttl;
    }

    public IssuedTicket issue(String sessionId, long accountId, String workCode, String origin) {
        String nonce = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
        nonce = nonce.substring(0, 64);
        String originValue = normalizeOrigin(origin);
        SandboxTicket ticket = new SandboxTicket(
                UUID.randomUUID().toString().replace("-", ""),
                CanonicalJson.sha256(nonce),
                sessionId,
                accountId,
                workCode,
                originValue,
                clock.instant().plus(ttl));
        store.insertTicket(ticket);
        return new IssuedTicket(ticket.ticketId(), nonce, originValue, ticket.expiresAt());
    }

    public SandboxTicket require(String nonce, String iframeOrigin, String method, long accountId, String sessionId, String workCode) {
        if (!SandboxMethods.allowed(method)) {
            throw RuntimeFailure.forbidden("METHOD_NOT_ALLOWED", "sandbox method rejected");
        }
        if (nonce == null || nonce.isBlank()) {
            throw RuntimeFailure.forbidden("NONCE_INVALID", "nonce required");
        }
        SandboxTicket ticket = store.findTicketByNonceHash(CanonicalJson.sha256(nonce))
                .orElseThrow(() -> RuntimeFailure.forbidden("NONCE_INVALID", "nonce rejected"));
        if (ticket.expiresAt().isBefore(clock.instant())) {
            throw RuntimeFailure.forbidden("NONCE_INVALID", "nonce expired");
        }
        if (ticket.accountId() != accountId) {
            throw RuntimeFailure.forbidden("CROSS_ACCOUNT", "sandbox ticket account mismatch");
        }
        if (!ticket.sessionId().equals(sessionId)) {
            throw RuntimeFailure.crossSession();
        }
        if (!ticket.workCode().equals(workCode)) {
            throw RuntimeFailure.crossWork();
        }
        if (!CanonicalJson.constantTimeEquals(ticket.origin(), normalizeOrigin(iframeOrigin))) {
            throw RuntimeFailure.forbidden("ORIGIN_REJECTED", "iframe origin rejected");
        }
        return ticket;
    }

    public static String normalizeOrigin(String origin) {
        if (origin == null || origin.isBlank() || "null".equalsIgnoreCase(origin.trim())) {
            return OPAQUE_ORIGIN;
        }
        return origin.trim();
    }

    public static boolean forbiddenIframeFlag(String sandboxAttr) {
        if (sandboxAttr == null) {
            return true;
        }
        String lower = sandboxAttr.toLowerCase(Locale.ROOT);
        return lower.contains("allow-same-origin");
    }

    public record IssuedTicket(String ticketId, String nonce, String origin, Instant expiresAt) {
    }
}
