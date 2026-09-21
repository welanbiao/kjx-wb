package com.koujunxin.work.jdbc;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

import com.koujunxin.work.catalog.ContentVersionRecord;
import com.koujunxin.work.catalog.ContentVersionRepository;
import com.koujunxin.work.catalog.DuplicateVersionException;

public class JdbcContentVersionRepository implements ContentVersionRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcContentVersionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public ContentVersionRecord insert(ContentVersionRecord record) {
        KeyHolder keys = new GeneratedKeyHolder();
        try {
            jdbcTemplate.update(con -> {
                PreparedStatement ps = con.prepareStatement("""
                        INSERT INTO kjx_content_version (
                          work_id, version, status, node_count, content_url, content,
                          review_time, publish_time, creator, updater
                        ) VALUES (?,?,?,?,?,CAST(? AS JSON),?,?,?,?)
                        """, Statement.RETURN_GENERATED_KEYS);
                int i = 1;
                ps.setLong(i++, record.workId());
                ps.setString(i++, record.version());
                ps.setString(i++, record.status());
                ps.setInt(i++, record.nodeCount());
                ps.setString(i++, record.contentUrl());
                ps.setString(i++, record.contentJson());
                ps.setTimestamp(i++, record.reviewTime() == null ? null : Timestamp.from(record.reviewTime()));
                ps.setTimestamp(i++, record.publishTime() == null ? null : Timestamp.from(record.publishTime()));
                ps.setString(i++, record.creator() == null ? "" : record.creator());
                ps.setString(i++, record.creator() == null ? "" : record.creator());
                return ps;
            }, keys);
        } catch (DuplicateKeyException ex) {
            throw new DuplicateVersionException(record.workId(), record.version());
        }
        return find(record.workId(), record.version()).orElseThrow();
    }

    @Override
    public Optional<ContentVersionRecord> find(long workId, String version) {
        List<ContentVersionRecord> rows = jdbcTemplate.query("""
                SELECT id, work_id, version, status, node_count, content_url, CAST(content AS CHAR) AS content,
                       review_time, publish_time, creator, create_time
                FROM kjx_content_version WHERE work_id = ? AND version = ?
                """, this::mapRow, workId, version);
        return rows.stream().findFirst();
    }

    @Override
    public List<ContentVersionRecord> listByWorkId(long workId) {
        return jdbcTemplate.query("""
                SELECT id, work_id, version, status, node_count, content_url, CAST(content AS CHAR) AS content,
                       review_time, publish_time, creator, create_time
                FROM kjx_content_version WHERE work_id = ? ORDER BY id DESC
                """, this::mapRow, workId);
    }

    @Override
    public int updateStatus(long workId, String version, String fromStatus, String toStatus) {
        return jdbcTemplate.update("""
                UPDATE kjx_content_version
                   SET status = ?,
                       publish_time = CASE WHEN ? = 'PUBLISHED' THEN CURRENT_TIMESTAMP(3) ELSE publish_time END
                 WHERE work_id = ? AND version = ? AND status = ?
                """, toStatus, toStatus, workId, version, fromStatus);
    }

    private ContentVersionRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
        Timestamp review = rs.getTimestamp("review_time");
        Timestamp publish = rs.getTimestamp("publish_time");
        Timestamp created = rs.getTimestamp("create_time");
        return new ContentVersionRecord(
                rs.getLong("id"),
                rs.getLong("work_id"),
                rs.getString("version"),
                rs.getString("status"),
                rs.getInt("node_count"),
                rs.getString("content_url"),
                rs.getString("content"),
                review == null ? null : review.toInstant(),
                publish == null ? null : publish.toInstant(),
                rs.getString("creator"),
                created == null ? null : created.toInstant());
    }
}
