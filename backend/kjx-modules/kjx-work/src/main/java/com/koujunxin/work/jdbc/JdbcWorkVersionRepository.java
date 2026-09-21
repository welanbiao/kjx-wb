package com.koujunxin.work.jdbc;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

import com.koujunxin.work.catalog.DuplicateVersionException;
import com.koujunxin.work.catalog.WorkVersionRecord;
import com.koujunxin.work.catalog.WorkVersionRepository;

public class JdbcWorkVersionRepository implements WorkVersionRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcWorkVersionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public WorkVersionRecord insert(WorkVersionRecord record) {
        KeyHolder keys = new GeneratedKeyHolder();
        try {
            jdbcTemplate.update(con -> {
                PreparedStatement ps = con.prepareStatement("""
                        INSERT INTO kjx_work_version (
                          work_id, version, status, form, runtime_compat, entry,
                          package_url, package_size, package_hash, gray_ratio, publish_time, creator, updater
                        ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)
                        """, Statement.RETURN_GENERATED_KEYS);
                int i = 1;
                ps.setLong(i++, record.workId());
                ps.setString(i++, record.version());
                ps.setString(i++, record.status());
                ps.setString(i++, record.form() == null ? "A" : record.form());
                ps.setString(i++, record.runtimeCompat());
                ps.setString(i++, record.entry());
                ps.setString(i++, record.packageUrl());
                if (record.packageSize() == null) {
                    ps.setObject(i++, null);
                } else {
                    ps.setLong(i++, record.packageSize());
                }
                ps.setString(i++, record.packageHash());
                ps.setInt(i++, record.grayRatio());
                ps.setTimestamp(i++, record.publishTime() == null ? null : Timestamp.from(record.publishTime()));
                ps.setString(i++, record.creator() == null ? "" : record.creator());
                ps.setString(i++, record.creator() == null ? "" : record.creator());
                return ps;
            }, keys);
        } catch (DuplicateKeyException ex) {
            throw new DuplicateVersionException(record.workId(), record.version());
        }
        Number id = keys.getKey();
        return find(record.workId(), record.version()).orElseGet(() ->
                new WorkVersionRecord(id == null ? null : id.longValue(), record.workId(), record.version(),
                        record.status(), record.form(), record.runtimeCompat(), record.entry(), record.packageUrl(),
                        record.packageSize(), record.packageHash(), record.grayRatio(), record.publishTime(),
                        record.creator(), record.createTime()));
    }

    @Override
    public Optional<WorkVersionRecord> find(long workId, String version) {
        List<WorkVersionRecord> rows = jdbcTemplate.query("""
                SELECT id, work_id, version, status, form, runtime_compat, entry, package_url, package_size,
                       package_hash, gray_ratio, publish_time, creator, create_time
                FROM kjx_work_version WHERE work_id = ? AND version = ?
                """, this::mapRow, workId, version);
        return rows.stream().findFirst();
    }

    @Override
    public List<WorkVersionRecord> listByWorkId(long workId) {
        return jdbcTemplate.query("""
                SELECT id, work_id, version, status, form, runtime_compat, entry, package_url, package_size,
                       package_hash, gray_ratio, publish_time, creator, create_time
                FROM kjx_work_version WHERE work_id = ? ORDER BY id DESC
                """, this::mapRow, workId);
    }

    @Override
    public int updateStatus(long workId, String version, String fromStatus, String toStatus) {
        return jdbcTemplate.update("""
                UPDATE kjx_work_version
                   SET status = ?, publish_time = CASE WHEN ? = 'PUBLISHED' THEN CURRENT_TIMESTAMP(3) ELSE publish_time END,
                       updater = updater
                 WHERE work_id = ? AND version = ? AND status = ?
                """, toStatus, toStatus, workId, version, fromStatus);
    }

    private WorkVersionRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
        Timestamp publish = rs.getTimestamp("publish_time");
        Timestamp created = rs.getTimestamp("create_time");
        return new WorkVersionRecord(
                rs.getLong("id"),
                rs.getLong("work_id"),
                rs.getString("version"),
                rs.getString("status"),
                rs.getString("form"),
                rs.getString("runtime_compat"),
                rs.getString("entry"),
                rs.getString("package_url"),
                rs.getObject("package_size") == null ? null : rs.getLong("package_size"),
                rs.getString("package_hash"),
                rs.getInt("gray_ratio"),
                publish == null ? null : publish.toInstant(),
                rs.getString("creator"),
                created == null ? null : created.toInstant());
    }
}
