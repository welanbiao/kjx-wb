package com.koujunxin.notification.outbox;

import java.time.Instant;

/**
 * Outbox 行快照。status：PENDING / PROCESSING / PUBLISHED / DEAD。
 * {@code envelopeJson} 为 {@code kjx_outbox_event.payload} 列内容，保存完整信封。
 */
public record OutboxRecord(
        String eventId,
        String aggregateType,
        String aggregateId,
        String eventType,
        String envelopeJson,
        String status,
        int retryCount,
        Instant nextRetryAt,
        String lastError,
        Instant createTime,
        Instant publishedAt
) {
}
