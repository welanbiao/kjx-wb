package com.koujunxin.notification.outbox.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
    void acceptsNotificationPayloadAndReturnsEnvelope() {
        Map<String, Object> payload = notificationPayload();
        Instant occurredAt = Instant.parse("2026-09-21T12:00:00Z");
        OutboxAppendCommand command = new OutboxAppendCommand(
                UUID.randomUUID().toString(),
                "notification",
                "12",
                "notification.requested",
                "1.0",
                occurredAt,
                "trace-1",
                "req-1",
                "idem-1",
                payload);
        Map<String, Object> envelope = validator.validate(command);
        assertThat(envelope.get("schemaVersion")).isEqualTo("1.0");
        assertThat(envelope.get("eventId")).isEqualTo(command.eventId());
        assertThat(envelope.get("traceId")).isEqualTo("trace-1");
        assertThat(envelope.get("requestId")).isEqualTo("req-1");
        assertThat(envelope.get("idempotencyKey")).isEqualTo("idem-1");
        assertThat(envelope.get("occurredAt")).isEqualTo("2026-09-21T20:00:00.000+08:00");
        @SuppressWarnings("unchecked")
        Map<String, Object> nested = (Map<String, Object>) envelope.get("payload");
        assertThat(nested.get("schemaVersion")).isEqualTo("1.0");
        assertThat(nested.get("templateCode")).isEqualTo("BINDING_APPROVED");
        assertThat(nested).doesNotContainKey("traceId");
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
    void rejectsEnvelopeSchemaVersion() {
        Map<String, Object> payload = notificationPayload();
        OutboxAppendCommand command = new OutboxAppendCommand(
                UUID.randomUUID().toString(),
                "notification",
                "12",
                "notification.requested",
                "2.0",
                Instant.now(),
                null, null, null,
                payload);
        assertThatThrownBy(() -> validator.validate(command))
                .isInstanceOf(OutboxValidationException.class)
                .hasMessageContaining("schemaVersion");
    }

    @Test
    void rejectsInvalidEventIdUuid() {
        OutboxAppendCommand command = new OutboxAppendCommand(
                "not-a-uuid",
                "notification",
                "12",
                "notification.requested",
                "1.0",
                Instant.now(),
                null, null, null,
                notificationPayload());
        assertThatThrownBy(() -> validator.validate(command))
                .isInstanceOf(OutboxValidationException.class)
                .hasMessageContaining("UUID");
    }

    @Test
    void rejectsOversizedEnvelopeFields() {
        String tooLong = "x".repeat(65);
        OutboxAppendCommand eventTypeTooLong = new OutboxAppendCommand(
                UUID.randomUUID().toString(),
                "notification",
                "12",
                tooLong,
                "1.0",
                Instant.now(),
                null, null, null,
                notificationPayload());
        assertThatThrownBy(() -> validator.validate(eventTypeTooLong))
                .isInstanceOf(OutboxValidationException.class)
                .hasMessageContaining("eventType");

        OutboxAppendCommand traceTooLong = new OutboxAppendCommand(
                UUID.randomUUID().toString(),
                "notification",
                "12",
                "notification.requested",
                "1.0",
                Instant.now(),
                tooLong, null, null,
                notificationPayload());
        assertThatThrownBy(() -> validator.validate(traceTooLong))
                .isInstanceOf(OutboxValidationException.class)
                .hasMessageContaining("traceId");

        OutboxAppendCommand requestTooLong = new OutboxAppendCommand(
                UUID.randomUUID().toString(),
                "notification",
                "12",
                "notification.requested",
                "1.0",
                Instant.now(),
                null, tooLong, null,
                notificationPayload());
        assertThatThrownBy(() -> validator.validate(requestTooLong))
                .isInstanceOf(OutboxValidationException.class)
                .hasMessageContaining("requestId");

        OutboxAppendCommand idemTooLong = new OutboxAppendCommand(
                UUID.randomUUID().toString(),
                "notification",
                "12",
                "notification.requested",
                "1.0",
                Instant.now(),
                null, null, tooLong,
                notificationPayload());
        assertThatThrownBy(() -> validator.validate(idemTooLong))
                .isInstanceOf(OutboxValidationException.class)
                .hasMessageContaining("idempotencyKey");
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
        Map<String, Object> payload = notificationPayload();
        payload.put("content", "验证码已发送到 13812345678");
        OutboxAppendCommand command = OutboxAppendCommand.of("notification", "12", "notification.requested", payload);
        assertThatThrownBy(() -> validator.validate(command))
                .isInstanceOf(OutboxValidationException.class);
    }

    @Test
    void rejectsSensitiveKey() {
        Map<String, Object> payload = notificationPayload();
        payload.put("token", "abc");
        OutboxAppendCommand command = OutboxAppendCommand.of("notification", "12", "notification.requested", payload);
        assertThatThrownBy(() -> validator.validate(command))
                .isInstanceOf(OutboxValidationException.class)
                .hasMessageContaining("sensitive");
    }

    @Test
    void rejectsNestedSensitiveKeyRecursively() {
        Map<String, Object> payload = notificationPayload();
        payload.put("variables", Map.of("nested", Map.of("access_token", "secret-value")));
        OutboxAppendCommand command = OutboxAppendCommand.of("notification", "12", "notification.requested", payload);
        assertThatThrownBy(() -> validator.validate(command))
                .isInstanceOf(OutboxValidationException.class)
                .hasMessageContaining("sensitive")
                .hasMessageContaining("variables.nested.access_token");
    }

    @Test
    void acceptsBillingAndPaymentEvents() {
        Map<String, Object> billingFrozen = validator.validate(
                OutboxAppendCommand.of("billing", "100", "billing.frozen", billingPayload()));
        assertThat(billingFrozen.get("aggregateType")).isEqualTo("billing");
        assertThat(billingFrozen.get("eventType")).isEqualTo("billing.frozen");

        Map<String, Object> billingSettled = validator.validate(
                OutboxAppendCommand.of("billing", "100", "billing.settled", billingPayload()));
        assertThat(billingSettled.get("eventType")).isEqualTo("billing.settled");

        Map<String, Object> billingReleased = validator.validate(
                OutboxAppendCommand.of("billing", "100", "billing.released", billingPayload()));
        assertThat(billingReleased.get("eventType")).isEqualTo("billing.released");

        Map<String, Object> paymentPaid = validator.validate(
                OutboxAppendCommand.of("payment", "out-1", "payment.paid", billingPayload()));
        assertThat(paymentPaid.get("aggregateType")).isEqualTo("payment");
        assertThat(paymentPaid.get("eventType")).isEqualTo("payment.paid");
        @SuppressWarnings("unchecked")
        Map<String, Object> nested = (Map<String, Object>) paymentPaid.get("payload");
        assertThat(nested.get("schemaVersion")).isEqualTo("1.0");
        assertThat(nested.get("memberId")).isEqualTo(12L);
        assertThat(nested.get("amount")).isEqualTo("10.00");
    }

    @Test
    void rejectsSensitiveFieldOnBillingAndPaymentEvents() {
        Map<String, Object> billingPayload = billingPayload();
        billingPayload.put("token", "abc");
        assertThatThrownBy(() -> validator.validate(
                OutboxAppendCommand.of("billing", "100", "billing.frozen", billingPayload)))
                .isInstanceOf(OutboxValidationException.class)
                .hasMessageContaining("sensitive");

        Map<String, Object> paymentPayload = billingPayload();
        paymentPayload.put("api_key", "sk-test");
        assertThatThrownBy(() -> validator.validate(
                OutboxAppendCommand.of("payment", "out-1", "payment.paid", paymentPayload)))
                .isInstanceOf(OutboxValidationException.class)
                .hasMessageContaining("sensitive");
    }

    private static Map<String, Object> notificationPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("schemaVersion", "1.0");
        payload.put("templateCode", "BINDING_APPROVED");
        payload.put("channels", List.of("INBOX"));
        payload.put("accountId", 12);
        payload.put("title", "绑定通过");
        return payload;
    }

    private static Map<String, Object> billingPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("schemaVersion", "1.0");
        payload.put("memberId", 12L);
        payload.put("amount", "10.00");
        return payload;
    }
}
