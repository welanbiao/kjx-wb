package com.koujunxin.notification.outbox;

import java.time.Instant;

/**
 * Outbox 行快照。status：PENDING / PROCESSING / PUBLISHED / DEAD。
 */
public record OutboxRecord(
        String eventId,
        String aggregateType,
        String aggregateId,
        String eventType,
        String payloadJson,
        String status,
        int retryCount,
        Instant nextRetryAt,
        String lastError,
        Instant createTime,
        Instant publishedAt
) {
}
