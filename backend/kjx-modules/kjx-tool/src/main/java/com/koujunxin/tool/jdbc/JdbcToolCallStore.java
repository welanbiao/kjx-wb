package com.koujunxin.tool.jdbc;

import java.sql.Timestamp;
import java.time.Instant;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import com.koujunxin.runtime.api.dto.ToolResult;
import com.koujunxin.tool.api.ToolFailure;
import com.koujunxin.tool.execute.CallClaim;
import com.koujunxin.tool.execute.ToolCallStore;

public final class JdbcToolCallStore implements ToolCallStore {

    private final JdbcTemplate jdbc;

    public JdbcToolCallStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public CallClaim claim(
            long accountId,
            String workCode,
            String sessionId,
            String toolCode,
            String toolVersion,
            String idempotencyKey,
            String argumentsHash,
            Instant startedAt,
            int pendingTtlMs) {
        try {
            jdbc.update(
                    """
                    INSERT INTO kjx_tool_call
                      (account_id, work_code, session_id, tool_code, tool_version, idempotency_key,
                       arguments_hash, status, started_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, 'PENDING', ?)
                    """,
                    accountId,
                    workCode,
                    sessionId,
                    toolCode,
                    toolVersion,
                    idempotencyKey,
                    argumentsHash,
                    Timestamp.from(startedAt));
            Long id = jdbc.queryForObject(
                    "SELECT id FROM kjx_tool_call WHERE session_id = ? AND idempotency_key = ?",
                    Long.class,
                    sessionId,
                    idempotencyKey);
            if (id == null) {
                throw new IllegalStateException("tool call id missing");
            }
            return CallClaim.fresh(id);
        } catch (DuplicateKeyException ex) {
            return replayOrTakeover(sessionId, idempotencyKey, argumentsHash, startedAt, pendingTtlMs);
        }
    }

    @Override
    public void complete(long id, ToolResult result, Instant finishedAt, int durationMs) {
        jdbc.update(
                """
                UPDATE kjx_tool_call
                   SET status = ?, result_code = ?, result_message = ?, result_payload = CAST(? AS JSON),
                       finished_at = ?, duration_ms = ?
                 WHERE id = ?
                """,
                result.success() ? "SUCCESS" : "FAILED",
                result.code(),
                result.message(),
                result.payloadJson(),
                Timestamp.from(finishedAt),
                durationMs,
                id);
    }

    private CallClaim replayOrTakeover(
            String sessionId,
            String idempotencyKey,
            String argumentsHash,
            Instant startedAt,
            int pendingTtlMs) {
        StoredCall row;
        try {
            row = jdbc.queryForObject(
                    """
                    SELECT id, arguments_hash, status, result_code, result_message, result_payload, started_at
                      FROM kjx_tool_call
                     WHERE session_id = ? AND idempotency_key = ?
                     FOR UPDATE
                    """,
                    (rs, n) -> new StoredCall(
                            rs.getLong("id"),
                            rs.getString("arguments_hash"),
                            rs.getString("status"),
                            rs.getString("result_code"),
                            rs.getString("result_message"),
                            rs.getString("result_payload"),
                            rs.getTimestamp("started_at").toInstant()),
                    sessionId,
                    idempotencyKey);
        } catch (EmptyResultDataAccessException ex) {
            throw ToolFailure.of("IDEMPOTENCY_CONFLICT", "idempotency row missing after conflict");
        }
        if (!argumentsHash.equals(row.argumentsHash())) {
            throw ToolFailure.of("IDEMPOTENCY_CONFLICT", "idempotency key reused with different arguments");
        }
        if ("PENDING".equals(row.status())) {
            boolean expired = pendingTtlMs > 0 && startedAt.toEpochMilli() - row.startedAt().toEpochMilli() > pendingTtlMs;
            if (!expired) {
                throw ToolFailure.of("TOOL_IN_PROGRESS", "same call is still running");
            }
            jdbc.update("UPDATE kjx_tool_call SET started_at = ?, status = 'PENDING' WHERE id = ?",
                    Timestamp.from(startedAt), row.id());
            return CallClaim.fresh(row.id());
        }
        boolean success = "SUCCESS".equals(row.status());
        String code = row.resultCode() == null || row.resultCode().isBlank() ? (success ? "OK" : "TOOL_FAILED") : row.resultCode();
        String payload = row.resultPayload() == null || row.resultPayload().isBlank() ? "{}" : row.resultPayload();
        String message = row.resultMessage() == null ? "" : row.resultMessage();
        return CallClaim.replay(row.id(), new ToolResult(success, code, message, payload));
    }

    private record StoredCall(
            long id,
            String argumentsHash,
            String status,
            String resultCode,
            String resultMessage,
            String resultPayload,
            Instant startedAt) {
    }
}
