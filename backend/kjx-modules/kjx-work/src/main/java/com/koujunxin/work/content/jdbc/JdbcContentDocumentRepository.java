package com.koujunxin.work.content.jdbc;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;

import com.koujunxin.work.content.ContentDocumentRecord;
import com.koujunxin.work.content.ContentDocumentRepository;

public final class JdbcContentDocumentRepository implements ContentDocumentRepository {

    private final JdbcTemplate jdbc;

    public JdbcContentDocumentRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public ContentDocumentRecord upsert(ContentDocumentRecord record) {
        jdbc.update("""
                INSERT INTO kjx_content_document (work_id, version, doc_type, content, node_count, creator)
                VALUES (?,?,?,CAST(? AS JSON),?,?)
                ON DUPLICATE KEY UPDATE content = CAST(VALUES(content) AS JSON), node_count = VALUES(node_count),
                  creator = VALUES(creator)
                """,
                record.workId(), record.version(), record.docType(), record.contentJson(),
                record.nodeCount(), record.creator() == null ? "" : record.creator());
        return find(record.workId(), record.version(), record.docType()).orElseThrow();
    }

    @Override
    public Optional<ContentDocumentRecord> find(long workId, String version, String docType) {
        List<ContentDocumentRecord> rows = jdbc.query("""
                SELECT id, work_id, version, doc_type, CAST(content AS CHAR) AS content, node_count, creator, create_time
                  FROM kjx_content_document WHERE work_id = ? AND version = ? AND doc_type = ?
                """, this::map, workId, version, docType);
        return rows.stream().findFirst();
    }

    @Override
    public List<ContentDocumentRecord> list(long workId, String version) {
        return jdbc.query("""
                SELECT id, work_id, version, doc_type, CAST(content AS CHAR) AS content, node_count, creator, create_time
                  FROM kjx_content_document WHERE work_id = ? AND version = ?
                """, this::map, workId, version);
    }

    private ContentDocumentRecord map(java.sql.ResultSet rs, int i) throws java.sql.SQLException {
        Timestamp created = rs.getTimestamp("create_time");
        return new ContentDocumentRecord(
                rs.getLong("id"),
                rs.getLong("work_id"),
                rs.getString("version"),
                rs.getString("doc_type"),
                rs.getString("content"),
                rs.getInt("node_count"),
                rs.getString("creator"),
                created == null ? null : created.toInstant());
    }
}
