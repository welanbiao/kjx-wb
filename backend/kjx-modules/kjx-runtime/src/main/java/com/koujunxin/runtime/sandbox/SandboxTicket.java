package com.koujunxin.runtime.sandbox;

import java.time.Instant;

public record SandboxTicket(
        String ticketId,
        String nonceHash,
        String sessionId,
        long accountId,
        String workCode,
        String origin,
        Instant expiresAt
) {
}
