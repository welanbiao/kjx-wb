package com.koujunxin.member.support;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

import com.koujunxin.work.api.WorkCatalogQueryPort;
import com.koujunxin.work.api.WorkSnapshot;
import com.koujunxin.work.catalog.AccessForm;
import com.koujunxin.work.catalog.WorkStatus;
import com.koujunxin.work.catalog.WorkType;

/**
 * 测试用作品目录。生产代码走 WP-12 {@code WorkCatalogQueryPort}，禁止 member 主代码访问 work Mapper。
 */
public final class JdbcTestWorkCatalog implements WorkCatalogQueryPort {

    private final JdbcTemplate jdbc;

    public JdbcTestWorkCatalog(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public long insert(String workCode, String name, WorkStatus status, int grayRatio) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(con -> {
            var ps = con.prepareStatement(
                    """
                    INSERT INTO kjx_work
                      (work_code, name, work_type, status, current_version, content_version, access_form,
                       gray_ratio, sort_weight, unlock_count, publish_time)
                    VALUES (?, ?, 'game', ?, '1.0.0', '1.0.0', 'A', ?, 0, 0, CURRENT_TIMESTAMP(3))
                    """,
                    java.sql.Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, workCode);
            ps.setString(2, name);
            ps.setString(3, status.name());
            ps.setInt(4, grayRatio);
            return ps;
        }, keys);
        Number id = keys.getKey();
        if (id == null) {
            throw new IllegalStateException("work insert failed");
        }
        return id.longValue();
    }

    public void setStatus(long workId, WorkStatus status) {
        jdbc.update("UPDATE kjx_work SET status = ? WHERE id = ?", status.name(), workId);
    }

    @Override
    public Optional<WorkSnapshot> findByCode(String workCode) {
        return jdbc.query(
                """
                SELECT id, work_code, name, status, current_version, gray_ratio, deleted
                  FROM kjx_work WHERE work_code = ? AND deleted = b'0'
                """,
                this::map, workCode).stream().findFirst();
    }

    @Override
    public Optional<WorkSnapshot> findById(long workId) {
        return jdbc.query(
                """
                SELECT id, work_code, name, status, current_version, gray_ratio, deleted
                  FROM kjx_work WHERE id = ? AND deleted = b'0'
                """,
                this::map, workId).stream().findFirst();
    }

    private WorkSnapshot map(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        boolean deleted = rs.getBoolean("deleted");
        return new WorkSnapshot(
                rs.getLong("id"),
                rs.getString("work_code"),
                rs.getString("name"),
                WorkType.game,
                null,
                null,
                List.of(),
                null,
                List.of(),
                WorkStatus.from(rs.getString("status")),
                rs.getString("current_version"),
                rs.getString("current_version"),
                AccessForm.A,
                List.of("PLATFORM"),
                rs.getInt("gray_ratio"),
                0,
                Instant.parse("2026-09-21T12:00:00Z"),
                deleted);
    }
}
