package com.koujunxin.work.asset.jdbc;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;

import com.koujunxin.work.asset.ObjectMetadata;
import com.koujunxin.work.asset.OssMetadataRepository;
import com.koujunxin.work.asset.StorageScope;

public final class JdbcOssMetadataRepository implements OssMetadataRepository {

    private final JdbcTemplate jdbc;

    public JdbcOssMetadataRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void upsert(ObjectMetadata metadata) {
        jdbc.update("""
                INSERT INTO kjx_oss_object (scope, object_key, size_bytes, sha256, content_type, work_id, version)
                VALUES (?,?,?,?,?,?,?)
                ON DUPLICATE KEY UPDATE size_bytes = VALUES(size_bytes), sha256 = VALUES(sha256),
                  content_type = VALUES(content_type), work_id = VALUES(work_id), version = VALUES(version)
                """,
                metadata.scope().name(),
                metadata.objectKey(),
                metadata.sizeBytes(),
                metadata.sha256(),
                metadata.contentType(),
                metadata.workId(),
                metadata.version());
    }

    @Override
    public Optional<ObjectMetadata> find(StorageScope scope, String objectKey) {
        List<ObjectMetadata> rows = jdbc.query("""
                SELECT scope, object_key, size_bytes, sha256, content_type, work_id, version, create_time
                  FROM kjx_oss_object WHERE scope = ? AND object_key = ?
                """, (rs, i) -> map(rs.getString("scope"), rs.getString("object_key"), rs.getLong("size_bytes"),
                        rs.getString("sha256"), rs.getString("content_type"),
                        rs.getObject("work_id") == null ? null : rs.getLong("work_id"),
                        rs.getString("version"), rs.getTimestamp("create_time")),
                scope.name(), objectKey);
        return rows.stream().findFirst();
    }

    @Override
    public List<ObjectMetadata> listByWork(long workId, String version) {
        return jdbc.query("""
                SELECT scope, object_key, size_bytes, sha256, content_type, work_id, version, create_time
                  FROM kjx_oss_object WHERE work_id = ? AND (? IS NULL OR version = ?)
                """, (rs, i) -> map(rs.getString("scope"), rs.getString("object_key"), rs.getLong("size_bytes"),
                        rs.getString("sha256"), rs.getString("content_type"),
                        rs.getObject("work_id") == null ? null : rs.getLong("work_id"),
                        rs.getString("version"), rs.getTimestamp("create_time")),
                workId, version, version);
    }

    private static ObjectMetadata map(String scope, String key, long size, String sha, String type, Long workId,
            String version, Timestamp created) {
        Instant at = created == null ? null : created.toInstant();
        return new ObjectMetadata(StorageScope.valueOf(scope), key, size, sha, type, workId, version, at);
    }
}
