package com.koujunxin.member.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;

import com.koujunxin.member.domain.HostRecord;
import com.koujunxin.member.domain.PageResult;

public class HostRepository {

    private final JdbcTemplate jdbc;

    public HostRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(HostRecord host, String actor) {
        jdbc.update(
                """
                INSERT INTO kjx_host
                  (dept_id, host_code, dy_account, logo_url, contact, remark, creator, updater)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                host.deptId(), host.hostCode(), host.dyAccount(), host.logoUrl(), host.contact(),
                host.remark(), actor, actor);
    }

    public int update(long deptId, String dyAccount, String logoUrl, String contact, String remark, String actor) {
        return jdbc.update(
                """
                UPDATE kjx_host
                   SET dy_account = COALESCE(?, dy_account),
                       logo_url = COALESCE(?, logo_url),
                       contact = COALESCE(?, contact),
                       remark = COALESCE(?, remark),
                       updater = ?
                 WHERE dept_id = ? AND deleted = b'0'
                """,
                dyAccount, logoUrl, contact, remark, actor, deptId);
    }

    public Optional<HostRecord> findByDeptId(long deptId) {
        return jdbc.query(
                """
                SELECT dept_id, host_code, dy_account, logo_url, contact, remark, create_time
                  FROM kjx_host
                 WHERE dept_id = ? AND deleted = b'0'
                """,
                this::map, deptId).stream().findFirst();
    }

    public Optional<HostRecord> findByHostCode(String hostCode) {
        return jdbc.query(
                """
                SELECT dept_id, host_code, dy_account, logo_url, contact, remark, create_time
                  FROM kjx_host
                 WHERE host_code = ? AND deleted = b'0'
                """,
                this::map, hostCode).stream().findFirst();
    }

    public List<HostRecord> listAll() {
        return jdbc.query(
                """
                SELECT dept_id, host_code, dy_account, logo_url, contact, remark, create_time
                  FROM kjx_host
                 WHERE deleted = b'0'
                 ORDER BY host_code
                """,
                this::map);
    }

    public PageResult<HostRecord> page(String hostCode, int pageNo, int pageSize) {
        int offset = Math.max(0, (pageNo - 1) * pageSize);
        String like = (hostCode == null || hostCode.isBlank()) ? null : hostCode.trim().toUpperCase();
        Long total = jdbc.queryForObject(
                """
                SELECT COUNT(*) FROM kjx_host
                 WHERE deleted = b'0' AND (? IS NULL OR host_code = ?)
                """,
                Long.class, like, like);
        List<HostRecord> list = jdbc.query(
                """
                SELECT dept_id, host_code, dy_account, logo_url, contact, remark, create_time
                  FROM kjx_host
                 WHERE deleted = b'0' AND (? IS NULL OR host_code = ?)
                 ORDER BY create_time DESC
                 LIMIT ? OFFSET ?
                """,
                this::map, like, like, pageSize, offset);
        return new PageResult<>(list, total == null ? 0 : total);
    }

    public List<HostRecord> findByCompactPrefix(String compactCode) {
        return jdbc.query(
                """
                SELECT dept_id, host_code, dy_account, logo_url, contact, remark, create_time
                  FROM kjx_host
                 WHERE deleted = b'0'
                   AND ? LIKE CONCAT(host_code, '%')
                   AND CHAR_LENGTH(?) = CHAR_LENGTH(host_code) + 8
                """,
                this::map, compactCode, compactCode);
    }

    private HostRecord map(ResultSet rs, int rowNum) throws SQLException {
        Timestamp created = rs.getTimestamp("create_time");
        return new HostRecord(
                rs.getLong("dept_id"),
                rs.getString("host_code"),
                rs.getString("dy_account"),
                rs.getString("logo_url"),
                rs.getString("contact"),
                rs.getString("remark"),
                created == null ? null : created.toLocalDateTime());
    }
}
