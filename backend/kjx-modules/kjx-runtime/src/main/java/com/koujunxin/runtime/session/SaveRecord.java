package com.koujunxin.runtime.session;

import java.time.Instant;

public record SaveRecord(
        long id,
        String sessionId,
        long accountId,
        String workCode,
        String slot,
        String schemaVersion,
        String contentVersion,
        String stage,
        String stateJson,
        String checksum,
        String signature,
        int version,
        Instant createdAt,
        Instant updatedAt
) {
}
