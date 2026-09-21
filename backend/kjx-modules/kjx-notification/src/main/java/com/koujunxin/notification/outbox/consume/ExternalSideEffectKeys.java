package com.koujunxin.notification.outbox.consume;

/**
 * 外部副作用去重键。Outbox 与外部 SMS 都是至少一次，不得声称 exactly-once。
 * 对支持幂等的短信提供方必须传 {@code eventId}。
 */
public final class ExternalSideEffectKeys {

    private ExternalSideEffectKeys() {
    }

    public static String smsProviderEventId(String eventId) {
        if (eventId == null || eventId.isBlank()) {
            throw new IllegalArgumentException("eventId is required");
        }
        return eventId.trim();
    }
}
