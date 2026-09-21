package com.koujunxin.log.mysql;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.koujunxin.log.api.port.AuditLogWritePort;
import com.koujunxin.log.redact.LogRedactor;

public final class JdbcAuditLogWriter implements AuditLogWritePort {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
            .withZone(ZoneId.of("Asia/Shanghai"));

    private static final String LAST_HASH_SQL = "SELECT hash FROM log_audit ORDER BY id DESC LIMIT 1 FOR UPDATE";

    private static final String INSERT_SQL = """
            INSERT INTO log_audit
              (log_id, ts, trace_id, request_id, admin_id, admin_name, roles, ip, ip_region,
               action, target_type, target_id, before_json, after_json, result, reason,
               prev_hash, hash, schema_version)
            VALUES (?, ?, ?, ?, ?, ?, CAST(? AS JSON), ?, ?, ?, ?, ?, CAST(? AS JSON), CAST(? AS JSON),
                    ?, ?, ?, ?, '1.0')
            """;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final LogRedactor redactor;
    private final AuditChainHasher hasher;

    public JdbcAuditLogWriter(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper, LogRedactor redactor) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.redactor = redactor;
        this.hasher = new AuditChainHasher();
    }

    @Override
    public void append(AuditLogCommand command) {
        requireTx();
        String logId = blankToUuid(command.logId());
        Instant ts = command.ts() == null ? Instant.now() : command.ts();
        @SuppressWarnings("unchecked")
        Map<String, Object> before = (Map<String, Object>) redactor.redact(command.before());
        @SuppressWarnings("unchecked")
        Map<String, Object> after = (Map<String, Object>) redactor.redact(command.after());
        String prev = jdbcTemplate.query(LAST_HASH_SQL, rs -> rs.next() ? rs.getString(1) : AuditChainHasher.GENESIS);
        if (prev == null) {
            prev = AuditChainHasher.GENESIS;
        }
        String hash = hasher.hash(prev, hasher.canonicalPayload(new AuditChainHasher.AuditRow(
                logId,
                TS.format(ts),
                require(command.traceId(), "traceId"),
                require(command.requestId(), "requestId"),
                command.adminId(),
                require(command.adminName(), "adminName"),
                require(command.action(), "action"),
                require(command.targetType(), "targetType"),
                require(command.targetId(), "targetId"),
                require(command.result(), "result"),
                command.reason())));
        jdbcTemplate.update(INSERT_SQL,
                logId,
                Timestamp.from(ts),
                command.traceId(),
                command.requestId(),
                command.adminId(),
                command.adminName(),
                json(command.roles() == null ? List.of() : command.roles()),
                IpBytes.from(command.ip()),
                command.ipRegion(),
                command.action(),
                command.targetType(),
                command.targetId(),
                json(before),
                json(after),
                command.result(),
                command.reason() == null ? null : redactor.redactText(command.reason()),
                prev,
                hash);
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

    private static void requireTx() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("AuditLogWritePort.append requires an active transaction");
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
