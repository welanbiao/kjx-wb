package com.koujunxin.server.integration;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;

import com.koujunxin.member.api.port.AccountBindingCommandPort;

final class JdbcAccountBindingCommandAdapter implements AccountBindingCommandPort {

    private static final String FIND_SQL = """
            SELECT id, status, bind_status, bind_fail_count, bind_locked_until
              FROM kjx_account
             WHERE id = ? AND deleted = b'0'
            """;

    private final JdbcTemplate jdbc;

    JdbcAccountBindingCommandAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<AccountBindingView> find(long accountId) {
        return jdbc.query(FIND_SQL, (rs, rowNum) -> {
            Timestamp locked = rs.getTimestamp("bind_locked_until");
            return new AccountBindingView(
                    rs.getLong("id"),
                    rs.getInt("status"),
                    rs.getInt("bind_status"),
                    rs.getInt("bind_fail_count"),
                    locked == null ? null : locked.toLocalDateTime());
        }, accountId).stream().findFirst();
    }

    @Override
    public void updateBindStatus(long accountId, int bindStatus) {
        requireOne(jdbc.update(
                "UPDATE kjx_account SET bind_status = ? WHERE id = ? AND deleted = b'0'",
                bindStatus, accountId), accountId);
    }

    @Override
    public void recordFailure(long accountId, int failCount, LocalDateTime lockedUntil) {
        int safeCount = Math.max(1, failCount);
        Timestamp lock = lockedUntil == null ? null : Timestamp.valueOf(lockedUntil);
        requireOne(jdbc.update(
                """
                UPDATE kjx_account
                   SET bind_locked_until = CASE
                           WHEN GREATEST(bind_fail_count + 1, ?) >= 3
                           THEN COALESCE(?, DATE_ADD(CURRENT_TIMESTAMP(3), INTERVAL 30 MINUTE))
                           ELSE bind_locked_until
                       END,
                       bind_fail_count = GREATEST(bind_fail_count + 1, ?)
                 WHERE id = ? AND deleted = b'0'
                """,
                safeCount, lock, safeCount, accountId), accountId);
    }

    @Override
    public void resetFailures(long accountId) {
        requireOne(jdbc.update(
                """
                UPDATE kjx_account
                   SET bind_fail_count = 0, bind_locked_until = NULL
                 WHERE id = ? AND deleted = b'0'
                """,
                accountId), accountId);
    }

    private static void requireOne(int updated, long accountId) {
        if (updated != 1) {
            throw new IllegalStateException("account binding state update failed for accountId=" + accountId);
        }
    }
}
