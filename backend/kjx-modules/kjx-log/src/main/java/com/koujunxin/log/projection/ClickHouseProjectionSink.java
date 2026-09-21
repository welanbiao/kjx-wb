package com.koujunxin.log.projection;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.koujunxin.notification.outbox.OutboxEnvelope;

public final class ClickHouseProjectionSink {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
            .withZone(ZoneId.of("Asia/Shanghai"));

    private final ClickHouseHttpClient client;
    private final ObjectMapper objectMapper;

    public ClickHouseProjectionSink(ClickHouseHttpClient client, ObjectMapper objectMapper) {
        this.client = client;
        this.objectMapper = objectMapper;
    }

    public void insert(OutboxEnvelope envelope) {
        String table = tableFor(envelope.eventType());
        Map<String, Object> row = toRow(envelope);
        try {
            client.insertJsonEachRow(table, objectMapper.writeValueAsString(row));
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("projection json failed", ex);
        }
    }

    public Long count(String table) {
        String body = client.query("SELECT count() FROM " + table);
        if (body == null || body.isBlank()) {
            return 0L;
        }
        try {
            return Long.parseLong(body.trim().split("\\s+")[0]);
        } catch (NumberFormatException ex) {
            return 0L;
        }
    }

    public void applySchema() {
        client.applyClasspathSchema("clickhouse/schema.sql");
    }

    private Map<String, Object> toRow(OutboxEnvelope envelope) {
        Map<String, Object> payload = envelope.payload() == null ? Map.of() : envelope.payload();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("event_id", envelope.eventId());
        row.put("ts", formatTs(payload.get("occurredAt"), envelope.occurredAt()));
        row.put("trace_id", first(payload.get("traceId"), envelope.traceId(), ""));
        row.put("request_id", first(payload.get("requestId"), envelope.requestId(), ""));
        switch (envelope.eventType()) {
            case "analytics.access.projected" -> {
                row.put("method", text(payload.get("method")));
                row.put("path", text(payload.get("path")));
                row.put("query_keys", stringArray(payload.get("queryKeys")));
                row.put("status", number(payload.get("status")));
                row.put("resp_bytes", number(payload.get("respBytes")));
                row.put("latency_ms", number(payload.get("latencyMs")));
                row.put("rate_limited", bool01(payload.get("rateLimited")));
                row.put("upstream_ms", number(payload.get("upstreamMs")));
                row.put("login_type", text(payload.get("loginType")));
                row.put("member_id", number(payload.get("memberId")));
                row.put("work_code", text(payload.get("workCode")));
            }
            case "analytics.ai_call.projected" -> {
                row.put("member_id", number(payload.get("memberId")));
                row.put("work_id", first(payload.get("workId"), payload.get("workCode"), ""));
                row.put("session_id", text(payload.get("sessionId")));
                row.put("turn_id", text(payload.get("turnId")));
                row.put("channel", text(payload.get("channel")));
                row.put("provider", text(payload.get("provider")));
                row.put("model", text(payload.get("model")));
                row.put("prompt_version_id", text(payload.get("promptVersionId")));
                row.put("prompt_mode", text(payload.get("promptMode")));
                row.put("messages_count", number(payload.get("messagesCount")));
                row.put("tool_defs_count", number(payload.get("toolDefsCount")));
                row.put("tools_enabled", stringArray(payload.get("toolsEnabled")));
                row.put("stream", bool01(payload.get("stream")));
                row.put("temperature", payload.get("temperature") == null ? 0 : payload.get("temperature"));
                row.put("max_tokens", number(payload.get("maxTokens")));
                row.put("status", first(payload.get("outcome"), payload.get("status"), ""));
                row.put("error_code", text(payload.get("errorCode")));
                row.put("degrade_level", text(payload.get("degradeLevel")));
                row.put("retry_count", number(payload.get("retryCount")));
                row.put("latency_ms", number(payload.get("latencyMs")));
                row.put("first_token_ms", number(payload.get("firstTokenMs")));
                row.put("input_tokens", number(payload.get("inputTokens")));
                row.put("output_tokens", number(payload.get("outputTokens")));
                row.put("cached_tokens", number(payload.get("cachedTokens")));
                row.put("reasoning_tokens", number(payload.get("reasoningTokens")));
                row.put("cost_amount", payload.get("costAmount") == null ? 0 : payload.get("costAmount"));
                row.put("charge_amount", payload.get("chargeAmount") == null ? 0 : payload.get("chargeAmount"));
            }
            case "analytics.tool_call.projected" -> {
                row.put("session_id", text(payload.get("sessionId")));
                row.put("tool_name", text(payload.get("toolName")));
                row.put("tool_version", text(payload.get("toolVersion")));
                row.put("tool_type", text(payload.get("toolType")));
                row.put("impl_kind", text(payload.get("implKind")));
                row.put("duration_ms", number(payload.get("durationMs")));
                row.put("ui_ops_count", number(payload.get("uiOpsCount")));
                row.put("outcome", text(payload.get("outcome")));
                row.put("reject_reason", text(payload.get("rejectReason")));
                row.put("member_id", number(payload.get("memberId")));
            }
            default -> throw new IllegalArgumentException("unsupported eventType: " + envelope.eventType());
        }
        return row;
    }

    static String tableFor(String eventType) {
        return switch (eventType) {
            case "analytics.access.projected" -> "kjx.log_access";
            case "analytics.ai_call.projected" -> "kjx.log_ai_call";
            case "analytics.tool_call.projected" -> "kjx.log_tool_call";
            default -> throw new IllegalArgumentException("unsupported eventType: " + eventType);
        };
    }

    private static String formatTs(Object occurredAt, Instant fallback) {
        Instant instant = fallback == null ? Instant.now() : fallback;
        if (occurredAt instanceof Instant value) {
            instant = value;
        } else if (occurredAt instanceof String text && !text.isBlank()) {
            instant = Instant.parse(text);
        }
        return TS.format(instant);
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static String first(Object... values) {
        for (Object value : values) {
            if (value != null && !String.valueOf(value).isBlank()) {
                return String.valueOf(value);
            }
        }
        return "";
    }

    private static long number(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Long.parseLong(text);
            } catch (NumberFormatException ignored) {
                return 0L;
            }
        }
        return 0L;
    }

    private static int bool01(Object value) {
        if (value instanceof Boolean bool) {
            return bool ? 1 : 0;
        }
        return 0;
    }

    private static Object stringArray(Object value) {
        if (value instanceof Collection<?> collection) {
            return collection.stream().map(String::valueOf).collect(Collectors.toList());
        }
        return java.util.List.of();
    }
}
