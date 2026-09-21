package com.koujunxin.account.api.port;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.koujunxin.account.config.AccountProperties;

class CaptchaPortAndDefaultsTest {

    @Test
    void captchaPortIsFailClosedByDefault() {
        assertFalse(new FailClosedCaptchaVerificationPort().verify("any", Map.of("token", "x")));
    }

    @Test
    void productionLockAndTtlDefaultsMatchWp10() {
        AccountProperties properties = AccountProperties.defaults();
        assertEquals(5, properties.loginFailLimit());
        assertEquals(Duration.ofMinutes(15), properties.loginLock());
        assertEquals(Duration.ofMinutes(5), properties.smsTtl());
        assertEquals(Duration.ofSeconds(60), properties.smsInterval());
        assertEquals(10, properties.smsDailyLimit());
        assertEquals(7, properties.cancelCoolingDays());
        assertEquals(2_592_000L, properties.tokenTtlSeconds());
    }
}
