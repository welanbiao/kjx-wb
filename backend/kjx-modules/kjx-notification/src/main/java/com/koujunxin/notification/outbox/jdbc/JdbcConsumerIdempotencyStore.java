package com.koujunxin.notification.outbox.jdbc;

import java.time.Duration;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

import com.koujunxin.notification.outbox.consume.ConsumerIdempotencyPort;

/**
 * 消费回执：PK (event_id, channel)。tryBegin 领取新行或回收过期 PROCESSING。
 * DONE 永不重复。外部 SMS 仍是至少一次。
 */
public class JdbcConsumerIdempotencyStore implements ConsumerIdempotencyPort {

    static final Duration DEFAULT_LEASE = Duration.ofSeconds(30);

    private static final String INSERT_SQL = """
            INSERT INTO kjx_outbox_consume_receipt
              (event_id, channel, status, attempt_count, lease_until)
            VALUES (?, ?, 'PROCESSING', 1, DATE_ADD(CURRENT_TIMESTAMP(3), INTERVAL ? SECOND))
            """;

    private static final String RECLAIM_SQL = """
            UPDATE kjx_outbox_consume_receipt
               SET status = 'PROCESSING',
                   attempt_count = attempt_count + 1,
                   lease_until = DATE_ADD(CURRENT_TIMESTAMP(3), INTERVAL ? SECOND),
                   update_time = CURRENT_TIMESTAMP(3)
             WHERE event_id = ? AND channel = ?
               AND status = 'PROCESSING'
               AND (lease_until IS NULL OR lease_until <= CURRENT_TIMESTAMP(3))
            """;

    private static final String COMPLETE_SQL = """
            UPDATE kjx_outbox_consume_receipt
               SET status = 'DONE',
                   lease_until = NULL,
                   update_time = CURRENT_TIMESTAMP(3)
             WHERE event_id = ? AND channel = ?
               AND status = 'PROCESSING'
            """;

    private static final String ABANDON_SQL = """
            DELETE FROM kjx_outbox_consume_receipt
             WHERE event_id = ? AND channel = ? AND status = 'PROCESSING'
            """;

    private static final String EXISTS_SQL = """
            SELECT COUNT(*) FROM kjx_outbox_consume_receipt
             WHERE event_id = ? AND channel = ? AND status = 'DONE'
            """;

    private final JdbcTemplate jdbcTemplate;
    private final Duration lease;

    public JdbcConsumerIdempotencyStore(JdbcTemplate jdbcTemplate) {
        this(jdbcTemplate, DEFAULT_LEASE);
    }

    public JdbcConsumerIdempotencyStore(JdbcTemplate jdbcTemplate, Duration lease) {
        this.jdbcTemplate = jdbcTemplate;
        this.lease = lease == null || lease.isNegative() || lease.isZero() ? DEFAULT_LEASE : lease;
    }

    @Override
    public boolean tryBegin(String eventId, String channel) {
        String ch = requireChannel(channel);
        requireEventId(eventId);
        long leaseSeconds = Math.max(1, lease.toSeconds());
        try {
            jdbcTemplate.update(INSERT_SQL, eventId, ch, leaseSeconds);
            return true;
        } catch (DuplicateKeyException ex) {
            return jdbcTemplate.update(RECLAIM_SQL, leaseSeconds, eventId, ch) == 1;
        }
    }

    @Override
    public void complete(String eventId, String channel) {
        jdbcTemplate.update(COMPLETE_SQL, eventId, requireChannel(channel));
    }

    @Override
    public void abandon(String eventId, String channel) {
        jdbcTemplate.update(ABANDON_SQL, eventId, requireChannel(channel));
    }

    @Override
    public boolean alreadyCompleted(String eventId, String channel) {
        Long count = jdbcTemplate.queryForObject(EXISTS_SQL, Long.class, eventId, requireChannel(channel));
        return count != null && count > 0;
    }

    private static void requireEventId(String eventId) {
        if (eventId == null || eventId.isBlank()) {
            throw new IllegalArgumentException("eventId is required");
        }
    }

    private static String requireChannel(String channel) {
        if (channel == null || channel.isBlank()) {
            throw new IllegalArgumentException("channel is required");
        }
        return channel.trim();
    }
}
