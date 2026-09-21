package com.koujunxin.account.jdbc;

import java.sql.Timestamp;
import java.time.LocalDateTime;

import org.springframework.jdbc.core.JdbcTemplate;

public class VerifyCodeAuditRepository {

    private final JdbcTemplate jdbc;

    public VerifyCodeAuditRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(String targetHash, String targetMask, String scene, String codeHash,
                       byte[] sendIp, LocalDateTime expireAt) {
        jdbc.update(
                """
                INSERT INTO kjx_verify_code
                  (target_hash, target_mask, scene, code_hash, send_ip, channel, expire_at)
                VALUES (?, ?, ?, ?, ?, 'SMS', ?)
                """,
                targetHash, targetMask, scene, codeHash, sendIp, Timestamp.valueOf(expireAt));
    }
}
