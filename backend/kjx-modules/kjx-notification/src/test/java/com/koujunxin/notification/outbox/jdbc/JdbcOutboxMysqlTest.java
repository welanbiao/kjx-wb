package com.koujunxin.notification.outbox.jdbc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.koujunxin.notification.outbox.OutboxAppendCommand;
import com.koujunxin.notification.outbox.OutboxProperties;
import com.koujunxin.notification.outbox.OutboxRecord;
import com.koujunxin.notification.outbox.OutboxRepository;
import com.koujunxin.notification.outbox.OutboxWriter;
import com.koujunxin.notification.outbox.support.FaultInjectingPublisher;
import com.koujunxin.notification.outbox.support.RecordingTestConsumer;

class JdbcOutboxMysqlTest extends MysqlOutboxSupport {

    private OutboxWriter writer;
    private OutboxRepository repository;
    private JdbcConsumerIdempotencyStore consumeStore;

    @BeforeEach
    void setUp() {
        ensureMigrated();
        truncateOutbox();
        writer = writer();
        repository = repository();
        consumeStore = new JdbcConsumerIdempotencyStore(jdbc());
    }

    @Test
    void appendRequiresSchemaVersionAndPersistsPending() {
        String eventId = tx().execute(status -> writer.append(notificationCommand()));
        String status = jdbc().queryForObject(
                "SELECT status FROM kjx_outbox_event WHERE event_id = ?", String.class, eventId);
        String payload = jdbc().queryForObject(
                "SELECT payload FROM kjx_outbox_event WHERE event_id = ?", String.class, eventId);
        assertThat(status).isEqualTo("PENDING");
        assertThat(payload).contains("schemaVersion");
    }

    @Test
    void businessRollbackLeavesNoOutboxRow() {
        OutboxAppendCommand command = notificationCommand();
        assertThatThrownBy(() -> tx().execute(status -> {
            writer.append(command);
            throw new IllegalStateException("business boom");
        })).hasMessageContaining("business boom");
        Long count = jdbc().queryForObject("SELECT COUNT(*) FROM kjx_outbox_event", Long.class);
        assertThat(count).isZero();
    }

    @Test
    void retryThenPublishAndDeadAfterMaxAttempts() {
        String eventId = tx().execute(status -> writer.append(notificationCommand()));
        RecordingTestConsumer consumer = new RecordingTestConsumer(consumeStore);
        OutboxProperties tight = new OutboxProperties(8, Duration.ofSeconds(30), 2, Duration.ofSeconds(5));

        FaultInjectingPublisher once = new FaultInjectingPublisher(consumer, 1);
        assertThat(worker(once, tight).drain()).isZero();
        assertThat(statusOf(eventId)).isEqualTo("PENDING");
        expireRetry(eventId);

        assertThat(worker(new FaultInjectingPublisher(consumer, 0), tight).drain()).isEqualTo(1);
        assertThat(statusOf(eventId)).isEqualTo("PUBLISHED");
        assertThat(consumer.consumed()).hasSize(1);

        String deadId = tx().execute(status -> writer.append(notificationCommand()));
        FaultInjectingPublisher alwaysFail = new FaultInjectingPublisher(consumer, 100);
        worker(alwaysFail, tight).drain();
        expireRetry(deadId);
        worker(alwaysFail, tight).drain();
        assertThat(statusOf(deadId)).isEqualTo("DEAD");
        String lastError = jdbc().queryForObject(
                "SELECT last_error FROM kjx_outbox_event WHERE event_id = ?", String.class, deadId);
        assertThat(lastError).isNotBlank().hasSizeLessThanOrEqualTo(500);
        assertThat(lastError).doesNotContain("13812345678");

        tx().executeWithoutResult(status -> repository.recoverDead(deadId));
        assertThat(statusOf(deadId)).isEqualTo("PENDING");
        assertThat(worker(consumer, tight).drain()).isEqualTo(1);
        assertThat(statusOf(deadId)).isEqualTo("PUBLISHED");
    }

    @Test
    void expiredLeaseCanBeRecoveredAndReclaimed() {
        String eventId = tx().execute(status -> writer.append(notificationCommand()));
        tx().executeWithoutResult(status -> repository.claim(1, Duration.ofSeconds(60)));
        assertThat(statusOf(eventId)).isEqualTo("PROCESSING");

        jdbc().update("UPDATE kjx_outbox_event SET next_retry_at = DATE_SUB(CURRENT_TIMESTAMP(3), INTERVAL 1 SECOND) WHERE event_id = ?",
                eventId);
        int recovered = tx().execute(status -> repository.recoverExpiredLeases());
        assertThat(recovered).isEqualTo(1);
        assertThat(statusOf(eventId)).isEqualTo("PENDING");

        RecordingTestConsumer consumer = new RecordingTestConsumer(consumeStore);
        assertThat(worker(consumer, OutboxProperties.defaults()).drain()).isEqualTo(1);
        assertThat(statusOf(eventId)).isEqualTo("PUBLISHED");
    }

    @Test
    void claimPicksExpiredProcessingLeaseWithoutRecover() {
        String eventId = tx().execute(status -> writer.append(notificationCommand()));
        tx().executeWithoutResult(status -> repository.claim(1, Duration.ofSeconds(60)));
        jdbc().update("UPDATE kjx_outbox_event SET next_retry_at = DATE_SUB(CURRENT_TIMESTAMP(3), INTERVAL 1 SECOND) WHERE event_id = ?",
                eventId);
        RecordingTestConsumer consumer = new RecordingTestConsumer(consumeStore);
        assertThat(worker(consumer, OutboxProperties.defaults()).drain()).isEqualTo(1);
        assertThat(statusOf(eventId)).isEqualTo("PUBLISHED");
    }

    @Test
    void duplicateDeliveryIsIdempotentPerEventAndChannel() throws Exception {
        String eventId = tx().execute(status -> writer.append(notificationCommand()));
        OutboxRecord record = tx().execute(status -> repository.claim(1, Duration.ofSeconds(30))).get(0);
        RecordingTestConsumer consumer = new RecordingTestConsumer(consumeStore);
        consumer.publish(record);
        consumer.publish(record);
        consumer.publish(record);
        assertThat(consumer.deliveryCount()).isEqualTo(3);
        assertThat(consumer.consumed()).hasSize(1);
        assertThat(consumeStore.alreadyCompleted(eventId, RecordingTestConsumer.CHANNEL)).isTrue();
    }

    @Test
    void appendWithoutTransactionFails() {
        assertThatThrownBy(() -> writer.append(notificationCommand()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("active transaction");
    }

    @Test
    void payloadMustContainSchemaVersion() {
        OutboxAppendCommand invalid = OutboxAppendCommand.of(
                "account",
                "1",
                "account.registered",
                java.util.Map.of("hello", "world"));
        assertThatThrownBy(() -> tx().execute(status -> writer.append(invalid)))
                .isInstanceOf(com.koujunxin.notification.outbox.OutboxValidationException.class);
    }

    private String statusOf(String eventId) {
        return jdbc().queryForObject("SELECT status FROM kjx_outbox_event WHERE event_id = ?", String.class, eventId);
    }

    private void expireRetry(String eventId) {
        jdbc().update(
                "UPDATE kjx_outbox_event SET next_retry_at = DATE_SUB(CURRENT_TIMESTAMP(3), INTERVAL 1 SECOND) WHERE event_id = ?",
                eventId);
    }

    @SuppressWarnings("unused")
    private String uniqueId() {
        return UUID.randomUUID().toString();
    }
}
