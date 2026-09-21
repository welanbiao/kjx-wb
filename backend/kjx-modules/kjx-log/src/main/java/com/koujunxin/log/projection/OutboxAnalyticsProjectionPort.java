package com.koujunxin.log.projection;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import com.koujunxin.log.api.port.AnalyticsProjectionPort;
import com.koujunxin.log.api.port.RequestContextPort;
import com.koujunxin.log.redact.LogRedactor;
import com.koujunxin.notification.outbox.OutboxAppendCommand;
import com.koujunxin.notification.outbox.OutboxWriter;

/**
 * 将分析事件写入 WP-02 Outbox。调用方须处于 JDBC 事务中。
 */
public final class OutboxAnalyticsProjectionPort implements AnalyticsProjectionPort {

    private final OutboxWriter outboxWriter;
    private final RequestContextPort requestContextPort;
    private final LogRedactor redactor;

    public OutboxAnalyticsProjectionPort(
            OutboxWriter outboxWriter,
            RequestContextPort requestContextPort,
            LogRedactor redactor) {
        this.outboxWriter = outboxWriter;
        this.requestContextPort = requestContextPort;
        this.redactor = redactor;
    }

    @Override
    public String projectAccess(Map<String, Object> payload) {
        return append("analytics.access.projected", "ACCESS", payload);
    }

    @Override
    public String projectAiCall(Map<String, Object> payload) {
        return append("analytics.ai_call.projected", "AI_CALL", payload);
    }

    @Override
    public String projectToolCall(Map<String, Object> payload) {
        return append("analytics.tool_call.projected", "TOOL_CALL", payload);
    }

    private String append(String eventType, String category, Map<String, Object> payload) {
        Map<String, Object> body = sanitize(payload, category);
        RequestContextPort.RequestContextSnapshot ctx = requestContextPort.current();
        String requestId = string(body.get("requestId"));
        if (requestId == null) {
            requestId = ctx.requestId();
            body.put("requestId", requestId);
        }
        String traceId = string(body.get("traceId"));
        if (traceId == null) {
            traceId = ctx.traceId();
            if (traceId != null) {
                body.put("traceId", traceId);
            }
        }
        String aggregateId = firstNonBlank(
                string(body.get("requestId")),
                string(body.get("sessionId")),
                UUID.randomUUID().toString());
        OutboxAppendCommand command = new OutboxAppendCommand(
                UUID.randomUUID().toString(),
                "analytics",
                trim(aggregateId, 64),
                eventType,
                OutboxAppendCommand.CURRENT_SCHEMA,
                Instant.now(),
                traceId,
                requestId,
                string(body.get("idempotencyKey")),
                body);
        return outboxWriter.append(command);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> sanitize(Map<String, Object> payload, String category) {
        Map<String, Object> raw = payload == null ? Map.of() : payload;
        Map<String, Object> redacted = (Map<String, Object>) redactor.redact(raw);
        Map<String, Object> body = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : redacted.entrySet()) {
            String key = LogRedactor.normalizeKey(entry.getKey());
            if (key.contains("authorization")
                    || key.contains("cookie")
                    || key.contains("apikey")
                    || key.contains("secret")
                    || "token".equals(key)
                    || "password".equals(key)
                    || "phone".equals(key)
                    || "verify_code".equals(key)
                    || "verifycode".equals(key)) {
                continue;
            }
            if (LogRedactor.REDACTED.equals(entry.getValue())) {
                continue;
            }
            body.put(entry.getKey(), entry.getValue());
        }
        body.put("schemaVersion", OutboxAppendCommand.CURRENT_SCHEMA);
        body.put("category", category);
        if (!body.containsKey("occurredAt") || body.get("occurredAt") == null) {
            body.put("occurredAt", Instant.now().toString());
        }
        return body;
    }

    private static String string(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static String trim(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}
