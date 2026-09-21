package com.koujunxin.notification.outbox.jdbc;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

import com.koujunxin.notification.outbox.consume.ConsumerIdempotencyPort;

public class JdbcConsumerIdempotencyStore implements ConsumerIdempotencyPort {

    private static final String INSERT_SQL = """
            INSERT INTO kjx_outbox_consume_receipt (event_id, channel, status)
            VALUES (?, ?, 'PROCESSING')
            """;

    private static final String COMPLETE_SQL = """
            UPDATE kjx_outbox_consume_receipt
               SET status = 'DONE'
             WHERE event_id = ? AND channel = ?
            """;

    private static final String DELETE_SQL = """
            DELETE FROM kjx_outbox_consume_receipt
             WHERE event_id = ? AND channel = ? AND status = 'PROCESSING'
            """;

    private static final String EXISTS_SQL = """
            SELECT COUNT(*) FROM kjx_outbox_consume_receipt
             WHERE event_id = ? AND channel = ? AND status = 'DONE'
            """;

    private final JdbcTemplate jdbcTemplate;

    public JdbcConsumerIdempotencyStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean tryBegin(String eventId, String channel) {
        try {
            jdbcTemplate.update(INSERT_SQL, eventId, requireChannel(channel));
            return true;
        } catch (DuplicateKeyException ex) {
            return false;
        }
    }

    @Override
    public void complete(String eventId, String channel) {
        jdbcTemplate.update(COMPLETE_SQL, eventId, requireChannel(channel));
    }

    @Override
    public void abandon(String eventId, String channel) {
        jdbcTemplate.update(DELETE_SQL, eventId, requireChannel(channel));
    }

    @Override
    public boolean alreadyCompleted(String eventId, String channel) {
        Long count = jdbcTemplate.queryForObject(EXISTS_SQL, Long.class, eventId, requireChannel(channel));
        return count != null && count > 0;
    }

    private static String requireChannel(String channel) {
        if (channel == null || channel.isBlank()) {
            throw new IllegalArgumentException("channel is required");
        }
        return channel.trim();
    }
}
