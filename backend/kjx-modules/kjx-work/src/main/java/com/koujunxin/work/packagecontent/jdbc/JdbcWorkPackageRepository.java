package com.koujunxin.work.packagecontent.jdbc;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;

import com.koujunxin.work.packagecontent.WorkPackageRecord;
import com.koujunxin.work.packagecontent.WorkPackageRepository;
import com.koujunxin.work.packagecontent.WorkPackageStatus;

public final class JdbcWorkPackageRepository implements WorkPackageRepository {

    private final JdbcTemplate jdbc;

    public JdbcWorkPackageRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public WorkPackageRecord upsert(WorkPackageRecord record) {
        jdbc.update("""
                INSERT INTO kjx_work_package (
                  work_id, version, status, form, package_hash, package_size, storage_key, cdn_prefix, server_prefix,
                  content_version, gray_ratio, previous_version, validate_report, preview_token, creator, updater
                ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,CAST(? AS JSON),?,?,?)
                ON DUPLICATE KEY UPDATE status = VALUES(status), form = VALUES(form),
                  package_hash = VALUES(package_hash), package_size = VALUES(package_size),
                  storage_key = VALUES(storage_key), cdn_prefix = VALUES(cdn_prefix),
                  server_prefix = VALUES(server_prefix), content_version = VALUES(content_version),
                  gray_ratio = VALUES(gray_ratio), previous_version = VALUES(previous_version),
                  validate_report = CAST(VALUES(validate_report) AS JSON),
                  preview_token = VALUES(preview_token), updater = VALUES(updater)
                """,
                record.workId(), record.version(), record.status().name(), record.form(),
                record.packageHash(), record.packageSize(), record.storageKey(), record.cdnPrefix(),
                record.serverPrefix(), record.contentVersion(), record.grayRatio(), record.previousVersion(),
                record.validateReportJson(), record.previewToken(),
                record.creator() == null ? "" : record.creator(),
                record.creator() == null ? "" : record.creator());
        return find(record.workId(), record.version()).orElseThrow();
    }

    @Override
    public Optional<WorkPackageRecord> find(long workId, String version) {
        List<WorkPackageRecord> rows = jdbc.query("""
                SELECT id, work_id, version, status, form, package_hash, package_size, storage_key, cdn_prefix,
                       server_prefix, content_version, gray_ratio, previous_version,
                       CAST(validate_report AS CHAR) AS validate_report, preview_token, creator, create_time
                  FROM kjx_work_package WHERE work_id = ? AND version = ?
                """, this::map, workId, version);
        return rows.stream().findFirst();
    }

    @Override
    public List<WorkPackageRecord> listByWorkId(long workId) {
        return jdbc.query("""
                SELECT id, work_id, version, status, form, package_hash, package_size, storage_key, cdn_prefix,
                       server_prefix, content_version, gray_ratio, previous_version,
                       CAST(validate_report AS CHAR) AS validate_report, preview_token, creator, create_time
                  FROM kjx_work_package WHERE work_id = ? ORDER BY id DESC
                """, this::map, workId);
    }

    @Override
    public int update(WorkPackageRecord record) {
        return jdbc.update("""
                UPDATE kjx_work_package
                   SET status = ?, gray_ratio = ?, previous_version = ?,
                       validate_report = CAST(? AS JSON), preview_token = ?, updater = updater
                 WHERE work_id = ? AND version = ?
                """,
                record.status().name(), record.grayRatio(), record.previousVersion(),
                record.validateReportJson(), record.previewToken(), record.workId(), record.version());
    }

    @Override
    public void touchVersionPackage(long workId, String version, String packageUrl, Long packageSize,
            String packageHash, String validateReportJson, int grayRatio) {
        jdbc.update("""
                UPDATE kjx_work_version
                   SET package_url = ?, package_size = ?, package_hash = ?,
                       validate_report = CAST(? AS JSON), gray_ratio = ?
                 WHERE work_id = ? AND version = ?
                """,
                packageUrl, packageSize, packageHash, validateReportJson, grayRatio, workId, version);
    }

    private WorkPackageRecord map(java.sql.ResultSet rs, int i) throws java.sql.SQLException {
        Timestamp created = rs.getTimestamp("create_time");
        Long size = rs.getObject("package_size") == null ? null : rs.getLong("package_size");
        return new WorkPackageRecord(
                rs.getLong("id"),
                rs.getLong("work_id"),
                rs.getString("version"),
                WorkPackageStatus.valueOf(rs.getString("status")),
                rs.getString("form"),
                rs.getString("package_hash"),
                size,
                rs.getString("storage_key"),
                rs.getString("cdn_prefix"),
                rs.getString("server_prefix"),
                rs.getString("content_version"),
                rs.getInt("gray_ratio"),
                rs.getString("previous_version"),
                rs.getString("validate_report"),
                rs.getString("preview_token"),
                rs.getString("creator"),
                created == null ? null : created.toInstant());
    }
}
