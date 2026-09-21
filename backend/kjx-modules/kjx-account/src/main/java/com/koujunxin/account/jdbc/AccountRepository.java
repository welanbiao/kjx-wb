package com.koujunxin.account.jdbc;

import java.net.InetAddress;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

import com.koujunxin.account.api.AccountFailures;
import com.koujunxin.account.domain.AccountRecord;

public class AccountRepository {

    private final JdbcTemplate jdbc;

    public AccountRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public long insert(String phoneCipher, String phoneHash, String passwordHash, String nickname,
                       byte[] registerIp, String registerDevice, LocalDateTime now) {
        KeyHolder keys = new GeneratedKeyHolder();
        try {
            jdbc.update(con -> {
                PreparedStatement ps = con.prepareStatement(
                        """
                        INSERT INTO kjx_account
                          (phone, phone_hash, password_hash, nickname, status, bind_status,
                           register_ip, register_device, last_login_at, last_login_ip, create_time)
                        VALUES (?, ?, ?, ?, 1, 0, ?, ?, ?, ?, ?)
                        """,
                        Statement.RETURN_GENERATED_KEYS);
                ps.setString(1, phoneCipher);
                ps.setString(2, phoneHash);
                ps.setString(3, passwordHash);
                ps.setString(4, nickname);
                ps.setBytes(5, registerIp);
                ps.setString(6, registerDevice);
                ps.setTimestamp(7, Timestamp.valueOf(now));
                ps.setBytes(8, registerIp);
                ps.setTimestamp(9, Timestamp.valueOf(now));
                return ps;
            }, keys);
        } catch (DuplicateKeyException ex) {
            throw AccountFailures.phoneTaken();
        }
        Number id = keys.getKey();
        if (id == null) {
            throw new IllegalStateException("account insert produced no id");
        }
        return id.longValue();
    }

    public Optional<AccountRecord> findByPhoneHash(String phoneHash) {
        return jdbc.query(
                """
                SELECT id, phone, phone_hash, password_hash, nickname, avatar, status, bind_status,
                       cancel_apply_at, last_login_at, create_time
                  FROM kjx_account
                 WHERE phone_hash = ? AND deleted = b'0'
                """,
                this::map, phoneHash).stream().findFirst();
    }

    public Optional<AccountRecord> findById(long id) {
        return jdbc.query(
                """
                SELECT id, phone, phone_hash, password_hash, nickname, avatar, status, bind_status,
                       cancel_apply_at, last_login_at, create_time
                  FROM kjx_account
                 WHERE id = ? AND deleted = b'0'
                """,
                this::map, id).stream().findFirst();
    }

    public void updatePassword(long id, String passwordHash) {
        jdbc.update("UPDATE kjx_account SET password_hash = ? WHERE id = ?", passwordHash, id);
    }

    public void updateProfile(long id, String nickname, String avatar) {
        jdbc.update(
                """
                UPDATE kjx_account
                   SET nickname = COALESCE(?, nickname),
                       avatar = COALESCE(?, avatar)
                 WHERE id = ?
                """,
                nickname, avatar, id);
    }

    public void markLogin(long id, byte[] ip, LocalDateTime now) {
        jdbc.update(
                "UPDATE kjx_account SET last_login_at = ?, last_login_ip = ? WHERE id = ?",
                Timestamp.valueOf(now), ip, id);
    }

    public void applyCancel(long id, LocalDateTime applyAt) {
        int updated = jdbc.update(
                """
                UPDATE kjx_account
                   SET status = 3, cancel_apply_at = ?
                 WHERE id = ? AND status = 1
                """,
                Timestamp.valueOf(applyAt), id);
        if (updated == 0) {
            throw AccountFailures.accountCancelling();
        }
    }

    public void revokeCancel(long id) {
        int updated = jdbc.update(
                """
                UPDATE kjx_account
                   SET status = 1, cancel_apply_at = NULL
                 WHERE id = ? AND status = 3
                """,
                id);
        if (updated == 0) {
            throw AccountFailures.badRequest("当前不在注销冷静期");
        }
    }

    public static byte[] ipBytes(String ip) {
        if (ip == null || ip.isBlank() || "unknown".equalsIgnoreCase(ip)) {
            return null;
        }
        try {
            return InetAddress.getByName(ip).getAddress();
        } catch (Exception ex) {
            return null;
        }
    }

    private AccountRecord map(ResultSet rs, int rowNum) throws SQLException {
        Timestamp cancel = rs.getTimestamp("cancel_apply_at");
        Timestamp lastLogin = rs.getTimestamp("last_login_at");
        Timestamp created = rs.getTimestamp("create_time");
        return new AccountRecord(
                rs.getLong("id"),
                rs.getString("phone"),
                rs.getString("phone_hash"),
                rs.getString("password_hash"),
                rs.getString("nickname"),
                rs.getString("avatar"),
                rs.getInt("status"),
                rs.getInt("bind_status"),
                cancel == null ? null : cancel.toLocalDateTime(),
                lastLogin == null ? null : lastLogin.toLocalDateTime(),
                created == null ? null : created.toLocalDateTime());
    }
}
