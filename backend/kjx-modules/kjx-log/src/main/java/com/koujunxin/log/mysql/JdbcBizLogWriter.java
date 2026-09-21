package com.koujunxin.log.mysql;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.koujunxin.log.api.port.BizLogWritePort;
import com.koujunxin.log.redact.LogRedactor;

public final class JdbcBizLogWriter implements BizLogWritePort {

    private static final String INSERT_SQL = """
            INSERT INTO log_biz
              (log_id, ts, trace_id, request_id, actor_type, member_id, work_id, action, outcome, reason, payload, schema_version)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS JSON), '1.0')
            """;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final LogRedactor redactor;

    public JdbcBizLogWriter(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper, LogRedactor redactor) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.redactor = redactor;
    }

    @Override
    public void append(BizLogCommand command) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("BizLogWritePort.append requires an active transaction");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) redactor.redact(command.payload());
        jdbcTemplate.update(INSERT_SQL,
                blankToUuid(command.logId()),
                Timestamp.from(command.ts() == null ? Instant.now() : command.ts()),
                require(command.traceId(), "traceId"),
                require(command.requestId(), "requestId"),
                require(command.actorType(), "actorType"),
                command.memberId(),
                command.workId(),
                require(command.action(), "action"),
                require(command.outcome(), "outcome"),
                command.reason() == null ? null : redactor.redactText(command.reason()),
                json(payload));
    }

    private String json(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("json serialization failed", ex);
        }
    }

    private static String require(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.trim();
    }

    private static String blankToUuid(String logId) {
        return logId == null || logId.isBlank() ? UUID.randomUUID().toString() : logId.trim();
    }
}
