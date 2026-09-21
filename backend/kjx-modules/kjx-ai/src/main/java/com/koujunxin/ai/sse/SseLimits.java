package com.koujunxin.ai.sse;

import java.time.Duration;

public record SseLimits(Duration heartbeat, Duration idleTimeout, Duration totalTimeout) {

    public SseLimits {
        if (heartbeat == null) {
            heartbeat = Duration.ofSeconds(15);
        }
        if (idleTimeout == null) {
            idleTimeout = Duration.ofSeconds(60);
        }
        if (totalTimeout == null) {
            totalTimeout = Duration.ofSeconds(120);
        }
    }

    public static SseLimits defaults() {
        return new SseLimits(Duration.ofSeconds(15), Duration.ofSeconds(60), Duration.ofSeconds(120));
    }
}
