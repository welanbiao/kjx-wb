package com.koujunxin.log.projection;

/**
 * ClickHouse 消费通道与外部副作用 eventId。复用 WP-02 ConsumerIdempotencyPort 语义。
 */
public final class ExternalProjectionKeys {

    public static final String CHANNEL_ACCESS = "ch.access";
    public static final String CHANNEL_AI_CALL = "ch.ai_call";
    public static final String CHANNEL_TOOL_CALL = "ch.tool_call";

    private ExternalProjectionKeys() {
    }

    public static String channelForEventType(String eventType) {
        if (eventType == null) {
            throw new IllegalArgumentException("eventType is required");
        }
        return switch (eventType) {
            case "analytics.access.projected" -> CHANNEL_ACCESS;
            case "analytics.ai_call.projected" -> CHANNEL_AI_CALL;
            case "analytics.tool_call.projected" -> CHANNEL_TOOL_CALL;
            default -> throw new IllegalArgumentException("not an analytics event: " + eventType);
        };
    }

    public static String clickHouseEventId(String eventId) {
        if (eventId == null || eventId.isBlank()) {
            throw new IllegalArgumentException("eventId is required");
        }
        return eventId.trim();
    }

    public static boolean isAnalytics(String eventType) {
        return eventType != null && eventType.startsWith("analytics.") && eventType.endsWith(".projected");
    }
}
