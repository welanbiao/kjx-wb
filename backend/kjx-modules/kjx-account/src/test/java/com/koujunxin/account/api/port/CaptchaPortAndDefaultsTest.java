package com.koujunxin.account.api.port;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import com.koujunxin.account.api.AccountException;
import com.koujunxin.account.config.AccountProperties;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

class CaptchaPortAndDefaultsTest {

    @Test
    void captchaPortIsFailClosedByDefault() {
        assertFalse(new FailClosedCaptchaVerificationPort().verify("any", Map.of("token", "x")));
    }

    @Test
    void smsDefaultPortIsFailClosedAndDoesNotLogSecrets() {
        Logger logger = (Logger) LoggerFactory.getLogger(NoOpSmsDispatchPort.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            AccountException ex = assertThrows(AccountException.class,
                    () -> new NoOpSmsDispatchPort().dispatch("register", "13800138000", "654321"));
            assertEquals(500, ex.bizCode());
            assertEquals(500, ex.httpStatus());
            String logs = appender.list.stream().map(ILoggingEvent::getFormattedMessage).collect(Collectors.joining());
            assertFalse(logs.contains("13800138000"));
            assertFalse(logs.contains("654321"));
            assertTrue(ex.getMessage() == null || (!ex.getMessage().contains("13800138000") && !ex.getMessage().contains("654321")));
        } finally {
            logger.detachAppender(appender);
        }
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
