package com.koujunxin.notification.outbox;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 投递给 {@link OutboxPublisher} 的完整信封，对齐 {@code outbox-envelope.schema.json}。
 * 业务体在嵌套 {@code payload} 中；表列 {@code kjx_outbox_event.payload} 保存本对象的 JSON。
 */
public record OutboxEnvelope(
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

    public static OutboxEnvelope fromJson(String json, ObjectMapper objectMapper) {
        if (json == null || json.isBlank()) {
            throw new OutboxValidationException("envelope json is required");
        }
        try {
            Map<String, Object> map = objectMapper.readValue(json, new TypeReference<>() {
            });
            return fromMap(map);
        } catch (JsonProcessingException ex) {
            throw new OutboxValidationException("envelope json is invalid");
        }
    }

    @SuppressWarnings("unchecked")
    public static OutboxEnvelope fromMap(Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            throw new OutboxValidationException("envelope is required");
        }
        Object payloadNode = map.get("payload");
        if (!(payloadNode instanceof Map<?, ?> payloadMap)) {
            throw new OutboxValidationException("envelope.payload must be an object");
        }
        return new OutboxEnvelope(
                text(map.get("eventId")),
                text(map.get("aggregateType")),
                text(map.get("aggregateId")),
                text(map.get("eventType")),
                text(map.get("schemaVersion")),
                parseOccurredAt(map.get("occurredAt")),
                optionalText(map.get("traceId")),
                optionalText(map.get("requestId")),
                optionalText(map.get("idempotencyKey")),
                new LinkedHashMap<>((Map<String, Object>) payloadMap));
    }

    public Map<String, Object> toMap() {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("eventId", eventId);
        envelope.put("aggregateType", aggregateType);
        envelope.put("aggregateId", aggregateId);
        envelope.put("eventType", eventType);
        envelope.put("schemaVersion", schemaVersion);
        envelope.put("occurredAt", formatOccurredAt(occurredAt));
        putOptional(envelope, "traceId", traceId);
        putOptional(envelope, "requestId", requestId);
        putOptional(envelope, "idempotencyKey", idempotencyKey);
        envelope.put("payload", payload == null ? Map.of() : payload);
        return envelope;
    }

    private static final DateTimeFormatter OCCURRED_AT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");

    public static String formatOccurredAt(Instant instant) {
        return instant.atOffset(ZoneOffset.ofHours(8)).format(OCCURRED_AT);
    }

    private static Instant parseOccurredAt(Object value) {
        if (value instanceof Instant instant) {
            return instant;
        }
        if (value instanceof OffsetDateTime offsetDateTime) {
            return offsetDateTime.toInstant();
        }
        if (value instanceof String text && !text.isBlank()) {
            return Instant.parse(text);
        }
        throw new OutboxValidationException("envelope.occurredAt must be ISO-8601");
    }

    private static String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static String optionalText(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private static void putOptional(Map<String, Object> target, String key, String value) {
        if (value != null && !value.isBlank()) {
            target.put(key, value);
        }
    }
}
