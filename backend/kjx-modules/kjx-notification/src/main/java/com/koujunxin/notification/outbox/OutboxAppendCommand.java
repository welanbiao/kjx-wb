package com.koujunxin.notification.outbox;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Outbox 追加命令。payload 必须含 schemaVersion，写入前做白名单与敏感字段校验。
 */
public record OutboxAppendCommand(
        String eventId,
        String aggregateType,
        String aggregateId,
        String eventType,
        String schemaVersion,
        Instant occurredAt,
        String traceId,
        String requestId,
        String idempotencyKey,
        Map<String, Object> payload
) {

    public static final String CURRENT_SCHEMA = "1.0";

    public OutboxAppendCommand {
        eventId = eventId == null || eventId.isBlank() ? UUID.randomUUID().toString() : eventId.trim();
        aggregateType = requireText(aggregateType, "aggregateType");
        aggregateId = requireText(aggregateId, "aggregateId");
        if (aggregateId.length() > 64) {
            throw new IllegalArgumentException("aggregateId exceeds 64 chars");
        }
        eventType = requireText(eventType, "eventType");
        schemaVersion = schemaVersion == null || schemaVersion.isBlank() ? CURRENT_SCHEMA : schemaVersion.trim();
        if (occurredAt == null) {
            occurredAt = Instant.now();
        }
        if (payload == null || payload.isEmpty()) {
            throw new IllegalArgumentException("payload is required");
        }
    }

    public static OutboxAppendCommand of(
            String aggregateType,
            String aggregateId,
            String eventType,
            Map<String, Object> payload) {
        return new OutboxAppendCommand(
                null, aggregateType, aggregateId, eventType, CURRENT_SCHEMA, Instant.now(),
                null, null, null, payload);
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.trim();
    }
}
