package com.koujunxin.notification.outbox;

import java.time.Duration;

public record OutboxProperties(
        int batchSize,
        Duration lease,
        int maxAttempts,
        Duration maxBackoff
) {

    public static OutboxProperties defaults() {
        return new OutboxProperties(16, Duration.ofSeconds(30), 8, Duration.ofHours(1));
    }
}
