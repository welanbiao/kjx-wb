package com.koujunxin.log.projection;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.containers.GenericContainer;

import com.koujunxin.log.api.port.ProjectionReconcilePort;
import com.koujunxin.log.metrics.NoOpObservabilityMetrics;
import com.koujunxin.log.redact.LogRedactor;
import com.koujunxin.log.support.MysqlLogSupport;
import com.koujunxin.notification.outbox.OutboxProperties;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ClickHouseProjectionFlowTest extends MysqlLogSupport {

    private GenericContainer<?> clickhouse;
    private ClickHouseProjectionSink sink;
    private ClickHouseProjectionPublisher publisher;

    @BeforeAll
    void startClickHouse() {
        ensureMigrated();
        clickhouse = ClickHouseTestSupport.container();
        sink = ClickHouseTestSupport.sink(clickhouse, mapper());
        publisher = projectionPublisher(sink);
    }

    @BeforeEach
    void clean() {
        truncateLogs();
        sink.applySchema();
        try {
            ClickHouseTestSupport.client(clickhouse).execute("TRUNCATE TABLE IF EXISTS kjx.log_access");
        } catch (RuntimeException ignored) {
            // first run
        }
    }

    @Test
    void duplicateConsumeDoesNotDuplicateRows() throws Exception {
        String eventId = tx().execute(status -> analyticsPort().projectAccess(accessPayload()));
        assertThat(worker(publisher, OutboxProperties.defaults()).drain()).isEqualTo(1);
        assertThat(countAccess()).isEqualTo(1);
        assertThat(consumeStore().alreadyCompleted(eventId, ExternalProjectionKeys.CHANNEL_ACCESS)).isTrue();

        publisher.publish(com.koujunxin.notification.outbox.OutboxEnvelope.fromJson(
                jdbc().queryForObject("SELECT payload FROM kjx_outbox_event WHERE event_id = ?", String.class, eventId),
                mapper()));
        assertThat(countAccess()).isEqualTo(1);
        assertThat(jdbc().queryForObject(
                "SELECT COUNT(*) FROM kjx_outbox_consume_receipt WHERE event_id = ? AND channel = ? AND status = 'DONE'",
                Long.class, eventId, ExternalProjectionKeys.CHANNEL_ACCESS)).isEqualTo(1);
    }

    @Test
    void clickhouseDownLeavesBacklogButMysqlBusinessSucceeds() {
        ClickHouseProjectionPublisher down = projectionPublisher(ClickHouseTestSupport.unreachableSink(mapper()));
        String eventId = tx().execute(status -> {
            auditWriter().append(auditCommand());
            return analyticsPort().projectAccess(accessPayload());
        });
        assertThat(jdbc().queryForObject("SELECT COUNT(*) FROM log_audit", Long.class)).isEqualTo(1);
        assertThat(jdbc().queryForObject(
                "SELECT status FROM kjx_outbox_event WHERE event_id = ?", String.class, eventId)).isEqualTo("PENDING");

        OutboxProperties tight = new OutboxProperties(8, Duration.ofSeconds(30), 2, Duration.ofSeconds(1));
        assertThat(worker(down, tight).drain()).isZero();
        String status = jdbc().queryForObject(
                "SELECT status FROM kjx_outbox_event WHERE event_id = ?", String.class, eventId);
        assertThat(status).isIn("PENDING", "DEAD");
        assertThat(jdbc().queryForObject("SELECT COUNT(*) FROM log_audit", Long.class)).isEqualTo(1);
        assertThat(countAccess()).isZero();
    }

    @Test
    void recoverReplayAndReconcile() {
        ClickHouseProjectionPublisher down = projectionPublisher(ClickHouseTestSupport.unreachableSink(mapper()));
        String eventId = tx().execute(status -> analyticsPort().projectAccess(accessPayload()));
        OutboxProperties tight = new OutboxProperties(8, Duration.ofSeconds(5), 1, Duration.ofSeconds(1));
        worker(down, tight).drain();
        assertThat(jdbc().queryForObject(
                "SELECT status FROM kjx_outbox_event WHERE event_id = ?", String.class, eventId)).isEqualTo("DEAD");

        tx().executeWithoutResult(status -> outboxRepository().recoverDead(eventId));
        assertThat(worker(publisher, OutboxProperties.defaults()).drain()).isEqualTo(1);
        assertThat(jdbc().queryForObject(
                "SELECT status FROM kjx_outbox_event WHERE event_id = ?", String.class, eventId)).isEqualTo("PUBLISHED");
        assertThat(countAccess()).isEqualTo(1);

        Instant end = Instant.now().plusSeconds(5);
        Instant start = end.minusSeconds(3600);
        JdbcProjectionReconciler reconciler = new JdbcProjectionReconciler(
                jdbc(), sink, new NoOpObservabilityMetrics(), new LogRedactor());
        ProjectionReconcilePort.ReconcileResult result = reconciler.reconcile("ACCESS", start, end);
        assertThat(result.mysqlPublished()).isEqualTo(1);
        assertThat(result.receiptDone()).isEqualTo(1);
        assertThat(result.clickhouseRows()).isEqualTo(1);
        assertThat(result.matched()).isTrue();
    }

    private long countAccess() {
        try {
            Long count = sink.count("kjx.log_access");
            return count == null ? 0 : count;
        } catch (RuntimeException ex) {
            return 0L;
        }
    }
}
