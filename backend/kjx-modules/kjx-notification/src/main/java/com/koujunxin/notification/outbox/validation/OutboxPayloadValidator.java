package com.koujunxin.notification.outbox.validation;

import java.io.InputStream;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.koujunxin.notification.outbox.OutboxAppendCommand;
import com.koujunxin.notification.outbox.OutboxValidationException;

/**
 * 按 contracts/events 白名单与 payload 强类型规则做边界校验。
 */
public class OutboxPayloadValidator {

    private static final Set<String> AGGREGATES = Set.of(
            "account", "member", "binding", "entitlement", "unlock", "work", "notification", "analytics");

    private static final Set<String> SENSITIVE_KEYS = Set.of(
            "phone", "mobile", "password", "token", "authorization", "cookie", "secret",
            "apikey", "api_key", "access_token", "refresh_token", "verifycode", "verify_code",
            "secretref", "id_token");

    private static final Set<String> NOTIFICATION_TEMPLATES = Set.of(
            "ACCOUNT_REGISTERED", "SECURITY_LOGIN", "SECURITY_PASSWORD_CHANGED",
            "BINDING_APPROVED", "BINDING_REJECTED", "BINDING_FORCE_UNBOUND",
            "MEMBER_UPDATED", "ENTITLEMENT_GRANTED", "ENTITLEMENT_REVOKED", "ACCOUNT_CANCEL_SCHEDULED");

    private static final Set<String> CHANNELS = Set.of("INBOX", "SMS");
    private static final Set<String> INBOX_TYPES = Set.of("SYSTEM", "SECURITY", "BENEFIT", "ACTIVITY");
    private static final Set<String> ENTITLEMENT_ACTIONS = Set.of("GRANTED", "REVOKED");
    private static final Set<String> ENTITLEMENT_SOURCES = Set.of("user", "official_add", "official_remove", "fix");
    private static final java.util.regex.Pattern PHONE = java.util.regex.Pattern.compile("(?<!\\d)1[3-9]\\d{9}(?!\\d)");

    private final Map<String, Set<String>> whitelist;
    private final ObjectMapper objectMapper;

    public OutboxPayloadValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.whitelist = loadWhitelist(objectMapper);
    }

    public Map<String, Object> validate(OutboxAppendCommand command) {
        if (!OutboxAppendCommand.CURRENT_SCHEMA.equals(command.schemaVersion())) {
            throw new OutboxValidationException("envelope schemaVersion must be 1.0");
        }
        if (!AGGREGATES.contains(command.aggregateType())) {
            throw new OutboxValidationException("aggregateType is not in envelope enum");
        }
        Set<String> allowed = whitelist.get(command.aggregateType());
        if (allowed == null || !allowed.contains(command.eventType())) {
            throw new OutboxValidationException("eventType is not in whitelist: " + command.eventType());
        }
        Map<String, Object> payload = command.payload();
        Object schemaVersion = payload.get("schemaVersion");
        if (!(schemaVersion instanceof String version) || !OutboxAppendCommand.CURRENT_SCHEMA.equals(version)) {
            throw new OutboxValidationException("payload.schemaVersion is required and must be 1.0");
        }
        rejectSensitive(payload, "payload");
        switch (command.eventType()) {
            case "notification.requested" -> validateNotification(payload);
            case "entitlement.granted", "entitlement.revoked" -> validateEntitlement(payload);
            case "analytics.access.projected",
                    "analytics.ai_call.projected",
                    "analytics.tool_call.projected" -> validateAnalytics(command.eventType(), payload);
            default -> {
                // 白名单内其他事件：至少 schemaVersion + 无敏感字段
            }
        }
        return payload;
    }

    private void validateNotification(Map<String, Object> payload) {
        require(payload, "templateCode");
        require(payload, "channels");
        require(payload, "accountId");
        String template = String.valueOf(payload.get("templateCode"));
        if (!NOTIFICATION_TEMPLATES.contains(template)) {
            throw new OutboxValidationException("unknown notification templateCode");
        }
        Object channels = payload.get("channels");
        if (!(channels instanceof Collection<?> list) || list.isEmpty()) {
            throw new OutboxValidationException("channels must be a non-empty array");
        }
        for (Object channel : list) {
            if (!CHANNELS.contains(String.valueOf(channel))) {
                throw new OutboxValidationException("channel must be INBOX or SMS");
            }
        }
        requirePositiveNumber(payload.get("accountId"), "accountId");
        if (payload.containsKey("inboxType") && !INBOX_TYPES.contains(String.valueOf(payload.get("inboxType")))) {
            throw new OutboxValidationException("invalid inboxType");
        }
        if (payload.containsKey("title") && String.valueOf(payload.get("title")).length() > 128) {
            throw new OutboxValidationException("title exceeds 128 chars");
        }
        if (payload.containsKey("content") && String.valueOf(payload.get("content")).length() > 1000) {
            throw new OutboxValidationException("content exceeds 1000 chars");
        }
    }

    private void validateEntitlement(Map<String, Object> payload) {
        require(payload, "action");
        require(payload, "source");
        require(payload, "memberId");
        require(payload, "deptId");
        require(payload, "workCode");
        require(payload, "occurredAt");
        if (!ENTITLEMENT_ACTIONS.contains(String.valueOf(payload.get("action")))) {
            throw new OutboxValidationException("invalid entitlement action");
        }
        if (!ENTITLEMENT_SOURCES.contains(String.valueOf(payload.get("source")))) {
            throw new OutboxValidationException("invalid entitlement source");
        }
        requirePositiveNumber(payload.get("memberId"), "memberId");
        requirePositiveNumber(payload.get("deptId"), "deptId");
        requireInstant(payload.get("occurredAt"), "occurredAt");
    }

    private void validateAnalytics(String eventType, Map<String, Object> payload) {
        require(payload, "category");
        require(payload, "occurredAt");
        require(payload, "requestId");
        requireInstant(payload.get("occurredAt"), "occurredAt");
        String category = String.valueOf(payload.get("category"));
        if (eventType.endsWith("access.projected") && !"ACCESS".equals(category)) {
            throw new OutboxValidationException("ACCESS projection category mismatch");
        }
        if (eventType.endsWith("ai_call.projected") && !"AI_CALL".equals(category)) {
            throw new OutboxValidationException("AI_CALL projection category mismatch");
        }
        if (eventType.endsWith("tool_call.projected") && !"TOOL_CALL".equals(category)) {
            throw new OutboxValidationException("TOOL_CALL projection category mismatch");
        }
        if ("ACCESS".equals(category)) {
            require(payload, "method");
            require(payload, "path");
            require(payload, "status");
        }
        if ("AI_CALL".equals(category)) {
            require(payload, "channel");
            require(payload, "provider");
            require(payload, "model");
        }
        if ("TOOL_CALL".equals(category)) {
            require(payload, "toolName");
            require(payload, "outcome");
        }
    }

    private void rejectSensitive(Object node, String path) {
        if (node instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = String.valueOf(entry.getKey()).toLowerCase(Locale.ROOT).replace("-", "_");
                if (SENSITIVE_KEYS.contains(key) || key.contains("password") || key.contains("authorization")) {
                    throw new OutboxValidationException("payload contains sensitive key: " + path + "." + entry.getKey());
                }
                rejectSensitive(entry.getValue(), path + "." + entry.getKey());
            }
        } else if (node instanceof Collection<?> collection) {
            int i = 0;
            for (Object item : collection) {
                rejectSensitive(item, path + "[" + i++ + "]");
            }
        } else if (node instanceof String text) {
            if (PHONE.matcher(text).find()) {
                throw new OutboxValidationException("payload contains plaintext phone at " + path);
            }
        }
    }

    private static void require(Map<String, Object> payload, String key) {
        if (!payload.containsKey(key) || payload.get(key) == null) {
            throw new OutboxValidationException("payload." + key + " is required");
        }
    }

    private static void requirePositiveNumber(Object value, String name) {
        if (!(value instanceof Number number) || number.longValue() < 1) {
            throw new OutboxValidationException(name + " must be a positive integer");
        }
    }

    private static void requireInstant(Object value, String name) {
        if (value instanceof Instant) {
            return;
        }
        if (value instanceof String text) {
            try {
                Instant.parse(text);
                return;
            } catch (DateTimeParseException ignored) {
                throw new OutboxValidationException(name + " must be ISO-8601");
            }
        }
        throw new OutboxValidationException(name + " must be ISO-8601");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Set<String>> loadWhitelist(ObjectMapper mapper) {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("contracts/events/event-whitelist.json")) {
            if (in == null) {
                throw new IllegalStateException("event-whitelist.json missing on classpath");
            }
            Map<String, Object> root = mapper.readValue(in, new TypeReference<>() {
            });
            Map<String, Object> aggregates = (Map<String, Object>) root.get("aggregates");
            Map<String, Set<String>> result = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : aggregates.entrySet()) {
                result.put(entry.getKey(), Set.copyOf((List<String>) entry.getValue()));
            }
            return Map.copyOf(result);
        } catch (OutboxValidationException | IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("failed to load event whitelist", ex);
        }
    }
}
