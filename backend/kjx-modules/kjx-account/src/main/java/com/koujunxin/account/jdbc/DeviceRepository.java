package com.koujunxin.account.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;

import com.koujunxin.account.domain.DeviceRecord;

public class DeviceRepository {

    private final JdbcTemplate jdbc;

    public DeviceRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void upsert(long accountId, String deviceId, String deviceName, String platform,
                       byte[] lastIp, String lastRegion, String tokenHash, LocalDateTime now) {
        jdbc.update(
                """
                INSERT INTO kjx_account_device
                  (account_id, device_id, device_name, platform, last_ip, last_region, last_active_at, token_hash)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                  device_name = VALUES(device_name),
                  platform = VALUES(platform),
                  last_ip = VALUES(last_ip),
                  last_region = VALUES(last_region),
                  last_active_at = VALUES(last_active_at),
                  token_hash = VALUES(token_hash)
                """,
                accountId, deviceId, deviceName, platform, lastIp, lastRegion, Timestamp.valueOf(now), tokenHash);
    }

    public List<DeviceRecord> listByAccount(long accountId) {
        return jdbc.query(
                """
                SELECT id, account_id, device_id, device_name, platform, last_region, last_active_at, token_hash
                  FROM kjx_account_device
                 WHERE account_id = ?
                 ORDER BY last_active_at DESC
                """,
                this::map, accountId);
    }

    public Optional<DeviceRecord> findById(long accountId, long deviceRowId) {
        return jdbc.query(
                """
                SELECT id, account_id, device_id, device_name, platform, last_region, last_active_at, token_hash
                  FROM kjx_account_device
                 WHERE id = ? AND account_id = ?
                """,
                this::map, deviceRowId, accountId).stream().findFirst();
    }

    public int delete(long accountId, long deviceRowId) {
        return jdbc.update(
                "DELETE FROM kjx_account_device WHERE id = ? AND account_id = ?",
                deviceRowId, accountId);
    }

    public void clearTokenHash(long accountId, String deviceId) {
        jdbc.update(
                "UPDATE kjx_account_device SET token_hash = NULL WHERE account_id = ? AND device_id = ?",
                accountId, deviceId);
    }

    private DeviceRecord map(ResultSet rs, int rowNum) throws SQLException {
        Timestamp active = rs.getTimestamp("last_active_at");
        return new DeviceRecord(
                rs.getLong("id"),
                rs.getLong("account_id"),
                rs.getString("device_id"),
                rs.getString("device_name"),
                rs.getString("platform"),
                rs.getString("last_region"),
                active == null ? null : active.toLocalDateTime(),
                rs.getString("token_hash"));
    }
}
