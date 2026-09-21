package com.koujunxin.member.jdbc;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

import com.koujunxin.member.domain.EntitlementRecord;
import com.koujunxin.member.domain.PageResult;

public class EntitlementRepository {

    private final JdbcTemplate jdbc;

    public EntitlementRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<EntitlementRecord> findByMemberAndWork(long memberId, long workId) {
        return jdbc.query(
                select() + " WHERE member_id = ? AND work_id = ?",
                this::map, memberId, workId).stream().findFirst();
    }

    public Optional<EntitlementRecord> findByAccountAndWork(long accountId, long workId) {
        return jdbc.query(
                select() + " WHERE account_id = ? AND work_id = ?",
                this::map, accountId, workId).stream().findFirst();
    }

    public Optional<EntitlementRecord> findAccessible(long accountId, long workId) {
        Optional<EntitlementRecord> byAccount = findByAccountAndWork(accountId, workId);
        if (byAccount.isPresent()) {
            return byAccount;
        }
        return jdbc.query(
                select() + """
                 WHERE work_id = ?
                   AND member_id IN (
                     SELECT id FROM kjx_member
                      WHERE deleted = b'0' AND bind_state = 1 AND bound_account_id = ?
                   )
                """,
                this::map, workId, accountId).stream().findFirst();
    }

    public boolean hasAccessible(long accountId, long workId) {
        return findAccessible(accountId, workId).isPresent();
    }

    public Set<Long> findWorkIdsByAccount(long accountId) {
        List<Long> ids = jdbc.query(
                """
                SELECT DISTINCT work_id FROM kjx_member_entitlement
                 WHERE account_id = ?
                    OR member_id IN (
                         SELECT id FROM kjx_member
                          WHERE deleted = b'0' AND bind_state = 1 AND bound_account_id = ?
                    )
                """,
                (rs, n) -> rs.getLong(1), accountId, accountId);
        return Set.copyOf(ids);
    }

    public Set<Long> findWorkIdsByAccount(long accountId, Collection<Long> workIds) {
        if (workIds == null || workIds.isEmpty()) {
            return Set.of();
        }
        Set<Long> wanted = new LinkedHashSet<>(workIds);
        Set<Long> entitled = findWorkIdsByAccount(accountId);
        entitled.retainAll(wanted);
        return Set.copyOf(entitled);
    }

    public int countByMemberId(long memberId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM kjx_member_entitlement WHERE member_id = ?",
                Integer.class, memberId);
        return count == null ? 0 : count;
    }

    public long insert(EntitlementRecord row) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    """
                    INSERT INTO kjx_member_entitlement
                      (dept_id, member_id, account_id, work_id, work_version, source, unlock_record_id, unlocked_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, row.deptId());
            ps.setLong(2, row.memberId());
            if (row.accountId() == null) {
                ps.setNull(3, Types.BIGINT);
            } else {
                ps.setLong(3, row.accountId());
            }
            ps.setLong(4, row.workId());
            ps.setString(5, row.workVersion());
            ps.setString(6, row.source());
            if (row.unlockRecordId() == null) {
                ps.setNull(7, Types.BIGINT);
            } else {
                ps.setLong(7, row.unlockRecordId());
            }
            ps.setTimestamp(8, Timestamp.valueOf(
                    row.unlockedAt() == null ? LocalDateTime.now() : row.unlockedAt()));
            return ps;
        }, keys);
        Number id = keys.getKey();
        if (id == null) {
            throw new IllegalStateException("entitlement insert produced no id");
        }
        return id.longValue();
    }

    public int deleteByMemberAndWork(long memberId, long workId) {
        return jdbc.update(
                "DELETE FROM kjx_member_entitlement WHERE member_id = ? AND work_id = ?",
                memberId, workId);
    }

    public PageResult<EntitlementRecord> pageByAccount(long accountId, int pageNo, int pageSize) {
        Integer total = jdbc.queryForObject(
                """
                SELECT COUNT(*) FROM kjx_member_entitlement
                 WHERE account_id = ?
                    OR member_id IN (
                         SELECT id FROM kjx_member
                          WHERE deleted = b'0' AND bind_state = 1 AND bound_account_id = ?
                    )
                """,
                Integer.class, accountId, accountId);
        int offset = Math.max(0, (pageNo - 1) * pageSize);
        List<EntitlementRecord> list = jdbc.query(
                select() + """
                 WHERE account_id = ?
                    OR member_id IN (
                         SELECT id FROM kjx_member
                          WHERE deleted = b'0' AND bind_state = 1 AND bound_account_id = ?
                    )
                 ORDER BY unlocked_at DESC, id DESC
                 LIMIT ? OFFSET ?
                """,
                this::map, accountId, accountId, pageSize, offset);
        return new PageResult<>(list, total == null ? 0 : total);
    }

    public List<EntitlementRecord> listByMember(long memberId) {
        return jdbc.query(select() + " WHERE member_id = ? ORDER BY unlocked_at DESC", this::map, memberId);
    }

    public Optional<EntitlementRecord> findById(long id) {
        return jdbc.query(select() + " WHERE id = ?", this::map, id).stream().findFirst();
    }

    private String select() {
        return """
                SELECT id, dept_id, member_id, account_id, work_id, work_version, source,
                       unlock_record_id, unlocked_at
                  FROM kjx_member_entitlement
                """;
    }

    private EntitlementRecord map(ResultSet rs, int rowNum) throws SQLException {
        long account = rs.getLong("account_id");
        boolean accountNull = rs.wasNull();
        long recordId = rs.getLong("unlock_record_id");
        boolean recordNull = rs.wasNull();
        Timestamp unlocked = rs.getTimestamp("unlocked_at");
        return new EntitlementRecord(
                rs.getLong("id"),
                rs.getLong("dept_id"),
                rs.getLong("member_id"),
                accountNull ? null : account,
                rs.getLong("work_id"),
                rs.getString("work_version"),
                rs.getString("source"),
                recordNull ? null : recordId,
                unlocked == null ? null : unlocked.toLocalDateTime());
    }
}
