package com.koujunxin.runtime.session;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import com.koujunxin.runtime.sandbox.SandboxTicket;

public final class JdbcRuntimeStore implements RuntimeStore {

    private final JdbcTemplate jdbc;

    public JdbcRuntimeStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insertSession(SessionRecord row) {
        jdbc.update(
                """
                INSERT INTO kjx_runtime_session (
                  session_id, account_id, work_code, work_id, form, stage, schema_version, content_version,
                  payload_json, checksum, status, version, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                row.sessionId(),
                row.accountId(),
                row.workCode(),
                row.workId(),
                row.form(),
                row.stage(),
                row.schemaVersion(),
                row.contentVersion(),
                row.payloadJson(),
                row.checksum(),
                row.status(),
                row.version(),
                timestamp(row.createdAt()),
                timestamp(row.updatedAt()));
    }

    @Override
    public Optional<SessionRecord> findSession(String sessionId) {
        List<SessionRecord> rows = jdbc.query(
                "SELECT * FROM kjx_runtime_session WHERE session_id = ?",
                SESSION,
                sessionId);
        return rows.stream().findFirst();
    }

    @Override
    public List<SessionRecord> listSessions(long accountId, String workCode) {
        return jdbc.query(
                """
                SELECT * FROM kjx_runtime_session
                WHERE account_id = ? AND work_code = ?
                ORDER BY updated_at DESC
                """,
                SESSION,
                accountId,
                workCode);
    }

    @Override
    public boolean updateSession(SessionRecord row, int expectedVersion) {
        int updated = jdbc.update(
                """
                UPDATE kjx_runtime_session
                SET stage = ?, payload_json = ?, checksum = ?, status = ?, version = version + 1, updated_at = ?
                WHERE session_id = ? AND account_id = ? AND work_code = ? AND version = ?
                """,
                row.stage(),
                row.payloadJson(),
                row.checksum(),
                row.status(),
                timestamp(row.updatedAt()),
                row.sessionId(),
                row.accountId(),
                row.workCode(),
                expectedVersion);
        return updated == 1;
    }

    @Override
    public void insertSave(SaveRecord row) {
        jdbc.update(
                """
                INSERT INTO kjx_runtime_save (
                  session_id, account_id, work_code, slot, schema_version, content_version, stage,
                  state_json, checksum, signature, version, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                row.sessionId(),
                row.accountId(),
                row.workCode(),
                row.slot(),
                row.schemaVersion(),
                row.contentVersion(),
                row.stage(),
                row.stateJson(),
                row.checksum(),
                row.signature(),
                row.version(),
                timestamp(row.createdAt()),
                timestamp(row.updatedAt()));
    }

    @Override
    public Optional<SaveRecord> findSave(String sessionId, String slot) {
        List<SaveRecord> rows = jdbc.query(
                "SELECT * FROM kjx_runtime_save WHERE session_id = ? AND slot = ?",
                SAVE,
                sessionId,
                slot);
        return rows.stream().findFirst();
    }

    @Override
    public List<SaveRecord> listSaves(long accountId, String workCode, String sessionId) {
        return jdbc.query(
                """
                SELECT * FROM kjx_runtime_save
                WHERE account_id = ? AND work_code = ? AND session_id = ?
                ORDER BY slot
                """,
                SAVE,
                accountId,
                workCode,
                sessionId);
    }

    @Override
    public boolean updateSave(SaveRecord row, int expectedVersion) {
        int updated = jdbc.update(
                """
                UPDATE kjx_runtime_save
                SET schema_version = ?, content_version = ?, stage = ?, state_json = ?, checksum = ?,
                    signature = ?, version = version + 1, updated_at = ?
                WHERE session_id = ? AND slot = ? AND account_id = ? AND work_code = ? AND version = ?
                """,
                row.schemaVersion(),
                row.contentVersion(),
                row.stage(),
                row.stateJson(),
                row.checksum(),
                row.signature(),
                timestamp(row.updatedAt()),
                row.sessionId(),
                row.slot(),
                row.accountId(),
                row.workCode(),
                expectedVersion);
        return updated == 1;
    }

    @Override
    public void insertTicket(SandboxTicket ticket) {
        jdbc.update(
                """
                INSERT INTO kjx_runtime_sandbox_ticket (
                  ticket_id, nonce_hash, session_id, account_id, work_code, origin, expires_at, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                ticket.ticketId(),
                ticket.nonceHash(),
                ticket.sessionId(),
                ticket.accountId(),
                ticket.workCode(),
                ticket.origin(),
                timestamp(ticket.expiresAt()),
                timestamp(Instant.now()));
    }

    @Override
    public Optional<SandboxTicket> findTicketByNonceHash(String nonceHash) {
        List<SandboxTicket> rows = jdbc.query(
                "SELECT * FROM kjx_runtime_sandbox_ticket WHERE nonce_hash = ?",
                TICKET,
                nonceHash);
        return rows.stream().findFirst();
    }

    private static Timestamp timestamp(Instant instant) {
        return Timestamp.from(instant == null ? Instant.now() : instant);
    }

    private static Instant instant(Timestamp timestamp) {
        return timestamp == null ? Instant.now() : timestamp.toInstant();
    }

    private static final RowMapper<SessionRecord> SESSION = (rs, rowNum) -> new SessionRecord(
            rs.getLong("id"),
            rs.getString("session_id"),
            rs.getLong("account_id"),
            rs.getString("work_code"),
            rs.getObject("work_id") == null ? null : rs.getLong("work_id"),
            rs.getString("form"),
            rs.getString("stage"),
            rs.getString("schema_version"),
            rs.getString("content_version"),
            rs.getString("payload_json"),
            rs.getString("checksum"),
            rs.getString("status"),
            rs.getInt("version"),
            instant(rs.getTimestamp("created_at")),
            instant(rs.getTimestamp("updated_at")));

    private static final RowMapper<SaveRecord> SAVE = (rs, rowNum) -> new SaveRecord(
            rs.getLong("id"),
            rs.getString("session_id"),
            rs.getLong("account_id"),
            rs.getString("work_code"),
            rs.getString("slot"),
            rs.getString("schema_version"),
            rs.getString("content_version"),
            rs.getString("stage"),
            rs.getString("state_json"),
            rs.getString("checksum"),
            rs.getString("signature"),
            rs.getInt("version"),
            instant(rs.getTimestamp("created_at")),
            instant(rs.getTimestamp("updated_at")));

    private static final RowMapper<SandboxTicket> TICKET = (rs, rowNum) -> new SandboxTicket(
            rs.getString("ticket_id"),
            rs.getString("nonce_hash"),
            rs.getString("session_id"),
            rs.getLong("account_id"),
            rs.getString("work_code"),
            rs.getString("origin"),
            instant(rs.getTimestamp("expires_at")));
}
