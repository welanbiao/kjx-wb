package com.koujunxin.server.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.stream.Collectors;

import org.dromara.sms4j.api.entity.SmsResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import com.koujunxin.account.api.AccountException;
import com.koujunxin.account.api.port.NoOpSmsDispatchPort;
import com.koujunxin.account.api.port.SmsDispatchPort;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

class SmsDispatchAdapterTest {

    private static final String PHONE = "13800138000";
    private static final String CODE = "654321";

    private ListAppender<ILoggingEvent> appender;
    private Logger sms4jLogger;
    private Logger stubLogger;
    private Logger noopLogger;

    @BeforeEach
    void captureLogs() {
        appender = new ListAppender<>();
        appender.start();
        sms4jLogger = (Logger) LoggerFactory.getLogger(Sms4jSmsDispatchPort.class);
        stubLogger = (Logger) LoggerFactory.getLogger(StubSmsDispatchPort.class);
        noopLogger = (Logger) LoggerFactory.getLogger(NoOpSmsDispatchPort.class);
        sms4jLogger.addAppender(appender);
        stubLogger.addAppender(appender);
        noopLogger.addAppender(appender);
    }

    @AfterEach
    void detach() {
        sms4jLogger.detachAppender(appender);
        stubLogger.detachAppender(appender);
        noopLogger.detachAppender(appender);
    }

    @Test
    void liveSuccessDoesNotLogPhoneOrCode() {
        SmsDispatchPort port = new Sms4jSmsDispatchPort((configId, phone, code) -> {
            assertEquals("kjx", configId);
            SmsResponse response = new SmsResponse();
            response.setSuccess(true);
            return response;
        }, "kjx", "alibaba");
        port.dispatch("register", PHONE, CODE);
        assertNoSecretsInLogs();
    }

    @Test
    void providerFailureFailsDispatch() {
        SmsDispatchPort port = new Sms4jSmsDispatchPort((configId, phone, code) -> {
            SmsResponse response = new SmsResponse();
            response.setSuccess(false);
            return response;
        }, "kjx", "alibaba");
        AccountException ex = assertThrows(AccountException.class, () -> port.dispatch("login", PHONE, CODE));
        assertEquals(500, ex.bizCode());
        assertNoSecretsInLogs();
        assertFalse(ex.getMessage().contains(PHONE));
        assertFalse(ex.getMessage().contains(CODE));
    }

    @Test
    void providerExceptionFailsDispatch() {
        SmsDispatchPort port = new Sms4jSmsDispatchPort((configId, phone, code) -> {
            throw new IllegalStateException("vendor down " + phone + code);
        }, "kjx", "alibaba");
        AccountException ex = assertThrows(AccountException.class, () -> port.dispatch("register", PHONE, CODE));
        assertEquals(500, ex.httpStatus());
        assertNoSecretsInLogs();
        assertFalse(ex.getMessage().contains(PHONE));
    }

    @Test
    void unconfiguredFailClosed() {
        SmsDispatchPort live = new Sms4jSmsDispatchPort(null, "", "alibaba");
        assertThrows(AccountException.class, () -> live.dispatch("register", PHONE, CODE));
        assertThrows(AccountException.class, () -> new NoOpSmsDispatchPort().dispatch("register", PHONE, CODE));
        assertNoSecretsInLogs();
    }

    @Test
    void stubDoesNotLogSecrets() {
        new StubSmsDispatchPort().dispatch("register", PHONE, CODE);
        assertNoSecretsInLogs();
        String logs = logs();
        assertTrue(logs.contains("scene=register") || logs.contains("stubbed"));
    }

    @Test
    void liveConfigurationRefusesBlankProviderOrConfigId() {
        SmsDispatchConfiguration configuration = new SmsDispatchConfiguration();
        KjxSmsProperties properties = new KjxSmsProperties();
        properties.setMode("live");
        properties.setProvider("");
        properties.setConfigId("");
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> configuration.liveSmsDispatchPort(properties, (id, phone, code) -> new SmsResponse()));
        assertTrue(ex.getMessage().contains("refusing silent degrade"));
    }

    private void assertNoSecretsInLogs() {
        String logs = logs();
        assertFalse(logs.contains(PHONE));
        assertFalse(logs.contains(CODE));
    }

    private String logs() {
        return appender.list.stream().map(ILoggingEvent::getFormattedMessage).collect(Collectors.joining("\n"));
    }
}
