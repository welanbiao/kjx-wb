package com.koujunxin.notification.outbox.jdbc;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.koujunxin.notification.outbox.OutboxRecord;
import com.koujunxin.notification.outbox.OutboxRepository;

public class JdbcOutboxRepository implements OutboxRepository {

    private static final String CLAIM_SELECT_SQL = """
            SELECT event_id, aggregate_type, aggregate_id, event_type, payload,
                   status, retry_count, next_retry_at, last_error, create_time, published_at
              FROM kjx_outbox_event
             WHERE (
                    status = 'PENDING'
                    AND (next_retry_at IS NULL OR next_retry_at <= CURRENT_TIMESTAMP(3))
                   )
                OR (
                    status = 'PROCESSING'
                    AND next_retry_at IS NOT NULL
                    AND next_retry_at <= CURRENT_TIMESTAMP(3)
                   )
             ORDER BY create_time ASC
             LIMIT ?
             FOR UPDATE SKIP LOCKED
            """;

    private static final String CLAIM_LEASE_SQL = """
            UPDATE kjx_outbox_event
               SET status = 'PROCESSING',
                   next_retry_at = DATE_ADD(CURRENT_TIMESTAMP(3), INTERVAL ? SECOND)
             WHERE event_id = ?
            """;

    private static final String PUBLISHED_SQL = """
            UPDATE kjx_outbox_event
               SET status = 'PUBLISHED',
                   published_at = CURRENT_TIMESTAMP(3),
                   last_error = NULL,
                   next_retry_at = NULL
             WHERE event_id = ?
               AND status = 'PROCESSING'
            """;

    private static final String RETRY_SQL = """
            UPDATE kjx_outbox_event
               SET status = 'PENDING',
                   retry_count = retry_count + 1,
                   next_retry_at = DATE_ADD(CURRENT_TIMESTAMP(3), INTERVAL ? SECOND),
                   last_error = ?
             WHERE event_id = ?
               AND status = 'PROCESSING'
            """;

    private static final String DEAD_SQL = """
            UPDATE kjx_outbox_event
               SET status = 'DEAD',
                   retry_count = retry_count + 1,
                   last_error = ?,
                   next_retry_at = NULL
             WHERE event_id = ?
               AND status IN ('PROCESSING', 'PENDING')
            """;

    private static final String RECOVER_LEASE_SQL = """
            UPDATE kjx_outbox_event
               SET status = 'PENDING'
             WHERE status = 'PROCESSING'
               AND next_retry_at IS NOT NULL
               AND next_retry_at <= CURRENT_TIMESTAMP(3)
            """;

    private static final String RECOVER_DEAD_SQL = """
            UPDATE kjx_outbox_event
               SET status = 'PENDING',
                   retry_count = 0,
                   last_error = NULL,
                   next_retry_at = CURRENT_TIMESTAMP(3)
             WHERE event_id = ?
               AND status = 'DEAD'
            """;

    private static final RowMapper<OutboxRecord> ROW_MAPPER = (rs, rowNum) -> new OutboxRecord(
            rs.getString("event_id"),
            rs.getString("aggregate_type"),
            rs.getString("aggregate_id"),
            rs.getString("event_type"),
            rs.getString("payload"),
            rs.getString("status"),
            rs.getInt("retry_count"),
            toInstant(rs.getTimestamp("next_retry_at")),
            rs.getString("last_error"),
            toInstant(rs.getTimestamp("create_time")),
            toInstant(rs.getTimestamp("published_at")));

    private final JdbcTemplate jdbcTemplate;

    public JdbcOutboxRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<OutboxRecord> claim(int batchSize, Duration lease) {
        requireTx();
        int size = Math.max(1, batchSize);
        long leaseSeconds = Math.max(1, lease.toSeconds());
        List<OutboxRecord> rows = jdbcTemplate.query(CLAIM_SELECT_SQL, ROW_MAPPER, size);
        List<OutboxRecord> claimed = new ArrayList<>(rows.size());
        for (OutboxRecord row : rows) {
            jdbcTemplate.update(CLAIM_LEASE_SQL, leaseSeconds, row.eventId());
            claimed.add(new OutboxRecord(
                    row.eventId(),
                    row.aggregateType(),
                    row.aggregateId(),
                    row.eventType(),
                    row.envelopeJson(),
                    "PROCESSING",
                    row.retryCount(),
                    Instant.now().plus(lease),
                    row.lastError(),
                    row.createTime(),
                    row.publishedAt()));
        }
        return List.copyOf(claimed);
    }

    @Override
    public void markPublished(String eventId) {
        requireTx();
        jdbcTemplate.update(PUBLISHED_SQL, eventId);
    }

    @Override
    public void markRetry(String eventId, String lastError, Duration delay) {
        requireTx();
        long seconds = Math.max(1, delay.toSeconds());
        jdbcTemplate.update(RETRY_SQL, seconds, lastError, eventId);
    }

    @Override
    public void markDead(String eventId, String lastError) {
        requireTx();
        jdbcTemplate.update(DEAD_SQL, lastError, eventId);
    }

    @Override
    public int recoverExpiredLeases() {
        requireTx();
        return jdbcTemplate.update(RECOVER_LEASE_SQL);
    }

    @Override
    public int recoverDead(String eventId) {
        requireTx();
        return jdbcTemplate.update(RECOVER_DEAD_SQL, eventId);
    }

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private static void requireTx() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("OutboxRepository requires an active transaction");
        }
    }
}
