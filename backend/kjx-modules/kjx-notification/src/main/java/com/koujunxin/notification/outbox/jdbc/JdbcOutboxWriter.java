package com.koujunxin.notification.outbox.jdbc;

import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.koujunxin.notification.outbox.OutboxAppendCommand;
import com.koujunxin.notification.outbox.OutboxValidationException;
import com.koujunxin.notification.outbox.OutboxWriter;
import com.koujunxin.notification.outbox.validation.OutboxPayloadValidator;

public class JdbcOutboxWriter implements OutboxWriter {

    private static final String INSERT_SQL = """
            INSERT INTO kjx_outbox_event
              (event_id, aggregate_type, aggregate_id, event_type, payload, status, retry_count, next_retry_at)
            VALUES (?, ?, ?, ?, CAST(? AS JSON), 'PENDING', 0, CURRENT_TIMESTAMP(3))
            """;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final OutboxPayloadValidator validator;

    public JdbcOutboxWriter(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper, OutboxPayloadValidator validator) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.validator = validator;
    }

    @Override
    public String append(OutboxAppendCommand command) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("OutboxWriter.append requires an active transaction");
        }
        Map<String, Object> envelope = validator.validate(command);
        try {
            String json = objectMapper.writeValueAsString(envelope);
            jdbcTemplate.update(INSERT_SQL,
                    command.eventId(),
                    command.aggregateType(),
                    command.aggregateId(),
                    command.eventType(),
                    json);
            return command.eventId();
        } catch (JsonProcessingException ex) {
            throw new OutboxValidationException("envelope is not valid json");
        }
    }
}
