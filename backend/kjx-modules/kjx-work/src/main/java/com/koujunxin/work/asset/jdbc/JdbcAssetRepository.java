package com.koujunxin.work.asset.jdbc;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;

import com.koujunxin.work.asset.AssetRecord;
import com.koujunxin.work.asset.AssetRepository;
import com.koujunxin.work.asset.StorageScope;

public final class JdbcAssetRepository implements AssetRepository {

    private final JdbcTemplate jdbc;

    public JdbcAssetRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public AssetRecord upsert(AssetRecord record) {
        jdbc.update("""
                INSERT INTO kjx_asset (
                  work_id, version, asset_key, path, content_type, size_bytes, sha256, width, height,
                  watermark_id, storage_key, scope, tags, license, creator
                ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                ON DUPLICATE KEY UPDATE content_type = VALUES(content_type), size_bytes = VALUES(size_bytes),
                  sha256 = VALUES(sha256), storage_key = VALUES(storage_key), scope = VALUES(scope),
                  tags = VALUES(tags), license = VALUES(license)
                """,
                record.workId(), record.version(), record.assetKey(), record.path(), record.contentType(),
                record.sizeBytes(), record.sha256(), record.width(), record.height(), record.watermarkId(),
                record.storageKey(), record.scope().name(), record.tags(), record.license(),
                record.creator() == null ? "" : record.creator());
        return findByPath(record.workId(), record.version(), record.path()).orElseThrow();
    }

    @Override
    public Optional<AssetRecord> findByPath(long workId, String version, String path) {
        List<AssetRecord> rows = jdbc.query("""
                SELECT id, work_id, version, asset_key, path, content_type, size_bytes, sha256, width, height,
                       watermark_id, storage_key, scope, tags, license, creator, create_time
                  FROM kjx_asset WHERE work_id = ? AND version = ? AND path = ?
                """, this::map, workId, version, path);
        return rows.stream().findFirst();
    }

    @Override
    public Optional<AssetRecord> findByKey(long workId, String version, String assetKey) {
        List<AssetRecord> rows = jdbc.query("""
                SELECT id, work_id, version, asset_key, path, content_type, size_bytes, sha256, width, height,
                       watermark_id, storage_key, scope, tags, license, creator, create_time
                  FROM kjx_asset WHERE work_id = ? AND version = ? AND asset_key = ?
                """, this::map, workId, version, assetKey);
        return rows.stream().findFirst();
    }

    @Override
    public List<AssetRecord> list(long workId, String version) {
        return jdbc.query("""
                SELECT id, work_id, version, asset_key, path, content_type, size_bytes, sha256, width, height,
                       watermark_id, storage_key, scope, tags, license, creator, create_time
                  FROM kjx_asset WHERE work_id = ? AND version = ?
                """, this::map, workId, version);
    }

    private AssetRecord map(java.sql.ResultSet rs, int i) throws java.sql.SQLException {
        Timestamp created = rs.getTimestamp("create_time");
        Integer width = rs.getObject("width") == null ? null : rs.getInt("width");
        Integer height = rs.getObject("height") == null ? null : rs.getInt("height");
        return new AssetRecord(
                rs.getLong("id"),
                rs.getLong("work_id"),
                rs.getString("version"),
                rs.getString("asset_key"),
                rs.getString("path"),
                rs.getString("content_type"),
                rs.getLong("size_bytes"),
                rs.getString("sha256"),
                width,
                height,
                rs.getString("watermark_id"),
                rs.getString("storage_key"),
                StorageScope.valueOf(rs.getString("scope")),
                rs.getString("tags"),
                rs.getString("license"),
                rs.getString("creator"),
                created == null ? null : created.toInstant());
    }
}
