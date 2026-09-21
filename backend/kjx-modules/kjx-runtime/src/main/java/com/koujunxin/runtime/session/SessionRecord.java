package com.koujunxin.runtime.session;

import java.time.Instant;

public record SessionRecord(
        long id,
        String sessionId,
        long accountId,
        String workCode,
        Long workId,
        String form,
        String stage,
        String schemaVersion,
        String contentVersion,
        String payloadJson,
        String checksum,
        String status,
        int version,
        Instant createdAt,
        Instant updatedAt
) {
}
