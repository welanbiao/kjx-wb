package com.koujunxin.member.support;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;

import com.koujunxin.member.api.port.AccountBindingCommandPort;

/**
 * 测试用账号绑定端口。生产环境由 WP-10 / kjx-server 提供等价实现，本模块不得直连 account Mapper。
 */
public final class JdbcAccountBindingCommandPort implements AccountBindingCommandPort {

    private final JdbcTemplate jdbc;

    public JdbcAccountBindingCommandPort(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public long insertAccount() {
        jdbc.update(
                """
                INSERT INTO kjx_account (phone, phone_hash, password_hash, nickname, status, bind_status)
                VALUES (?, ?, 'x', 'u', 1, 0)
                """,
                "c-" + java.util.UUID.randomUUID(), hex());
        Long id = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        if (id == null) {
            throw new IllegalStateException("account insert failed");
        }
        return id;
    }

    @Override
    public Optional<AccountBindingView> find(long accountId) {
        return jdbc.query(
                """
                SELECT id, status, bind_status, bind_fail_count, bind_locked_until
                  FROM kjx_account
                 WHERE id = ? AND deleted = b'0'
                """,
                (rs, n) -> {
                    Timestamp locked = rs.getTimestamp("bind_locked_until");
                    return new AccountBindingView(
                            rs.getLong("id"),
                            rs.getInt("status"),
                            rs.getInt("bind_status"),
                            rs.getInt("bind_fail_count"),
                            locked == null ? null : locked.toLocalDateTime());
                },
                accountId).stream().findFirst();
    }

    @Override
    public void updateBindStatus(long accountId, int bindStatus) {
        jdbc.update("UPDATE kjx_account SET bind_status = ? WHERE id = ?", bindStatus, accountId);
    }

    @Override
    public void recordFailure(long accountId, int failCount, LocalDateTime lockedUntil) {
        jdbc.update(
                "UPDATE kjx_account SET bind_fail_count = ?, bind_locked_until = ? WHERE id = ?",
                failCount, lockedUntil == null ? null : Timestamp.valueOf(lockedUntil), accountId);
    }

    @Override
    public void resetFailures(long accountId) {
        jdbc.update("UPDATE kjx_account SET bind_fail_count = 0, bind_locked_until = NULL WHERE id = ?", accountId);
    }

    private static String hex() {
        return (java.util.UUID.randomUUID().toString() + java.util.UUID.randomUUID().toString())
                .replace("-", "");
    }
}
