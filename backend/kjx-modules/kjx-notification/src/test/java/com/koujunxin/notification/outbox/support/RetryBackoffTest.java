package com.koujunxin.notification.outbox.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;

class RetryBackoffTest {

    @Test
    void exponentialWithJitterStaysWithinCap() {
        Duration max = Duration.ofSeconds(30);
        Duration d0 = RetryBackoff.delay(0, max);
        Duration d3 = RetryBackoff.delay(3, max);
        Duration d20 = RetryBackoff.delay(20, max);
        assertThat(d0.toMillis()).isBetween(1000L, 30_000L);
        assertThat(d3.toMillis()).isGreaterThanOrEqualTo(d0.toMillis() - 1000);
        assertThat(d20.toMillis()).isLessThanOrEqualTo(max.toMillis() + max.toMillis() / 5 + 1);
    }
}
