package com.koujunxin.notification.outbox.support;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 指数退避并加入抖动。
 */
public final class RetryBackoff {

    private RetryBackoff() {
    }

    public static Duration delay(int retryCount, Duration maxBackoff) {
        int exp = Math.min(Math.max(retryCount, 0), 12);
        long baseMs = Math.min(maxBackoff.toMillis(), (1L << exp) * 1000L);
        long jitter = ThreadLocalRandom.current().nextLong(0, Math.max(1, baseMs / 5 + 1));
        return Duration.ofMillis(baseMs + jitter);
    }
}
