package com.koujunxin.notification.outbox.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.koujunxin.notification.outbox.OutboxAppendCommand;
import com.koujunxin.notification.outbox.OutboxValidationException;

class OutboxPayloadValidatorTest {

    private OutboxPayloadValidator validator;

    @BeforeEach
    void setUp() {
        validator = new OutboxPayloadValidator(new ObjectMapper());
    }

    @Test
    void acceptsNotificationPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("schemaVersion", "1.0");
        payload.put("templateCode", "BINDING_APPROVED");
        payload.put("channels", List.of("INBOX"));
        payload.put("accountId", 12);
        payload.put("title", "绑定通过");
        OutboxAppendCommand command = OutboxAppendCommand.of("notification", "12", "notification.requested", payload);
        assertThat(validator.validate(command).get("schemaVersion")).isEqualTo("1.0");
    }

    @Test
    void rejectsMissingSchemaVersion() {
        Map<String, Object> payload = Map.of(
                "templateCode", "BINDING_APPROVED",
                "channels", List.of("INBOX"),
                "accountId", 12);
        OutboxAppendCommand command = OutboxAppendCommand.of("notification", "12", "notification.requested", payload);
        assertThatThrownBy(() -> validator.validate(command))
                .isInstanceOf(OutboxValidationException.class)
                .hasMessageContaining("schemaVersion");
    }

    @Test
    void rejectsUnknownEventType() {
        Map<String, Object> payload = Map.of("schemaVersion", "1.0");
        OutboxAppendCommand command = OutboxAppendCommand.of("notification", "12", "notification.unknown", payload);
        assertThatThrownBy(() -> validator.validate(command))
                .isInstanceOf(OutboxValidationException.class)
                .hasMessageContaining("whitelist");
    }

    @Test
    void rejectsSensitivePhone() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("schemaVersion", "1.0");
        payload.put("templateCode", "SECURITY_LOGIN");
        payload.put("channels", List.of("SMS"));
        payload.put("accountId", 12);
        payload.put("content", "验证码已发送到 13812345678");
        OutboxAppendCommand command = OutboxAppendCommand.of("notification", "12", "notification.requested", payload);
        assertThatThrownBy(() -> validator.validate(command))
                .isInstanceOf(OutboxValidationException.class);
    }

    @Test
    void rejectsSensitiveKey() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("schemaVersion", "1.0");
        payload.put("templateCode", "SECURITY_LOGIN");
        payload.put("channels", List.of("INBOX"));
        payload.put("accountId", 12);
        payload.put("token", "abc");
        OutboxAppendCommand command = OutboxAppendCommand.of("notification", "12", "notification.requested", payload);
        assertThatThrownBy(() -> validator.validate(command))
                .isInstanceOf(OutboxValidationException.class)
                .hasMessageContaining("sensitive");
    }
}
