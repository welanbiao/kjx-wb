package com.koujunxin.member.jdbc;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

import com.koujunxin.member.domain.MemberRecord;
import com.koujunxin.member.domain.PageResult;
import com.koujunxin.member.recalc.MemberRecalcService;

public class MemberRepository {

    private final JdbcTemplate jdbc;

    public MemberRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public long insert(MemberRecord member, String actor) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    """
                    INSERT INTO kjx_member
                      (member_code, douyin_id, douyin_id_raw, dept_id, subscribed_months, subscribed_days,
                       expire_at, start_at, available_count, unlocked_count, remain_count, status,
                       bind_state, remark, creator, updater)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?, ?)
                    """,
                    Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, member.memberCode());
            ps.setString(2, member.douyinId());
            ps.setString(3, member.douyinIdRaw());
            ps.setLong(4, member.deptId());
            ps.setInt(5, member.subscribedMonths());
            ps.setInt(6, member.subscribedDays());
            ps.setTimestamp(7, Timestamp.valueOf(member.expireAt()));
            ps.setTimestamp(8, member.startAt() == null ? null : Timestamp.valueOf(member.startAt()));
            ps.setInt(9, member.availableCount());
            ps.setInt(10, member.unlockedCount());
            ps.setInt(11, member.remainCount());
            ps.setString(12, member.status());
            ps.setString(13, member.remark());
            ps.setString(14, actor);
            ps.setString(15, actor);
            return ps;
        }, keys);
        Number id = keys.getKey();
        if (id == null) {
            throw new IllegalStateException("member insert produced no id");
        }
        return id.longValue();
    }

    public Optional<MemberRecord> findById(long id) {
        return jdbc.query(select() + " WHERE id = ? AND deleted = b'0'", this::map, id).stream().findFirst();
    }

    public Optional<MemberRecord> lockById(long id) {
        return jdbc.query(select() + " WHERE id = ? AND deleted = b'0' FOR UPDATE", this::map, id).stream().findFirst();
    }

    public Optional<MemberRecord> findByCodeAndDept(String compactCode, long deptId) {
        return jdbc.query(
                select() + """
                 WHERE deleted = b'0'
                   AND dept_id = ?
                   AND REPLACE(member_code, '-', '') = ?
                """,
                this::map, deptId, compactCode).stream().findFirst();
    }

    public Optional<MemberRecord> matchTriple(long deptId, String compactCode, String douyinId, int claimMonths) {
        return jdbc.query(
                select() + """
                 WHERE deleted = b'0'
                   AND dept_id = ?
                   AND REPLACE(member_code, '-', '') = ?
                   AND douyin_id = ?
                   AND subscribed_months = ?
                 LIMIT 1
                """,
                this::map, deptId, compactCode, douyinId, claimMonths).stream().findFirst();
    }

    public Optional<MemberRecord> findBoundByAccount(long accountId) {
        return jdbc.query(
                select() + " WHERE deleted = b'0' AND bind_state = 1 AND bound_account_id = ? LIMIT 1",
                this::map, accountId).stream().findFirst();
    }

    public Optional<MemberRecord> findByBoundAccountId(long accountId) {
        return jdbc.query(
                select() + """
                 WHERE deleted = b'0' AND bound_account_id = ?
                 ORDER BY bind_state DESC, id DESC
                 LIMIT 1
                """,
                this::map, accountId).stream().findFirst();
    }

    public void applyRecalc(long id, MemberRecalcService.RecalcResult recalc, LocalDateTime startAt, String actor) {
        jdbc.update(
                """
                UPDATE kjx_member
                   SET available_count = ?, unlocked_count = ?, remain_count = ?, status = ?, start_at = ?,
                       updater = ?, version = version + 1
                 WHERE id = ? AND deleted = b'0'
                """,
                recalc.availableCount(), recalc.unlockedCount(), recalc.remainCount(), recalc.status(),
                startAt == null ? null : Timestamp.valueOf(startAt), actor, id);
    }

    public void updateFields(long id, Integer months, Integer days, LocalDateTime expireAt, String remark,
                             MemberRecalcService.RecalcResult recalc, LocalDateTime startAt, String actor) {
        jdbc.update(
                """
                UPDATE kjx_member
                   SET subscribed_months = COALESCE(?, subscribed_months),
                       subscribed_days = COALESCE(?, subscribed_days),
                       expire_at = COALESCE(?, expire_at),
                       remark = COALESCE(?, remark),
                       available_count = ?,
                       unlocked_count = ?,
                       remain_count = ?,
                       status = ?,
                       start_at = ?,
                       updater = ?,
                       version = version + 1
                 WHERE id = ? AND deleted = b'0'
                """,
                months,
                days,
                expireAt == null ? null : Timestamp.valueOf(expireAt),
                remark,
                recalc.availableCount(),
                recalc.unlockedCount(),
                recalc.remainCount(),
                recalc.status(),
                startAt == null ? null : Timestamp.valueOf(startAt),
                actor,
                id);
    }

    public int markBound(long id, long accountId, LocalDateTime boundAt) {
        return jdbc.update(
                """
                UPDATE kjx_member
                   SET bind_state = 1, bound_account_id = ?, bound_at = ?, version = version + 1
                 WHERE id = ? AND deleted = b'0' AND bind_state = 0
                """,
                accountId, Timestamp.valueOf(boundAt), id);
    }

    /**
     * 新解锁额度 CAS。必须单 SQL：禁止先 SELECT 再 UPDATE。
     */
    public int casIncrementUnlocked(long memberId) {
        return jdbc.update(
                """
                UPDATE kjx_member
                   SET unlocked_count = unlocked_count + 1,
                       remain_count   = remain_count - 1,
                       version        = version + 1,
                       update_time    = CURRENT_TIMESTAMP(3)
                 WHERE id = ?
                   AND deleted = b'0'
                   AND status = 'VALID'
                   AND unlocked_count < available_count
                """,
                memberId);
    }

    public int markUnbound(long id) {
        return jdbc.update(
                """
                UPDATE kjx_member
                   SET bind_state = 0, bound_account_id = NULL, bound_at = NULL, version = version + 1
                 WHERE id = ? AND deleted = b'0' AND bind_state = 1
                """,
                id);
    }

    public PageResult<MemberRecord> page(Long deptId, String douyinId, String memberCode, String status,
                                         Integer bindState, int pageNo, int pageSize) {
        StringBuilder where = new StringBuilder(" WHERE deleted = b'0'");
        List<Object> args = new ArrayList<>();
        if (deptId != null) {
            where.append(" AND dept_id = ?");
            args.add(deptId);
        }
        if (douyinId != null && !douyinId.isBlank()) {
            where.append(" AND douyin_id = ?");
            args.add(douyinId.trim().toLowerCase());
        }
        if (memberCode != null && !memberCode.isBlank()) {
            where.append(" AND REPLACE(member_code, '-', '') = ?");
            args.add(memberCode.replace(" ", "").replace("-", "").toUpperCase());
        }
        if (status != null && !status.isBlank()) {
            where.append(" AND status = ?");
            args.add(status);
        }
        if (bindState != null) {
            where.append(" AND bind_state = ?");
            args.add(bindState);
        }
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM kjx_member" + where, Long.class, args.toArray());
        int offset = Math.max(0, (pageNo - 1) * pageSize);
        args.add(pageSize);
        args.add(offset);
        List<MemberRecord> list = jdbc.query(
                select() + where + " ORDER BY create_time DESC LIMIT ? OFFSET ?",
                this::map, args.toArray());
        return new PageResult<>(list, total == null ? 0 : total);
    }

    public List<MemberRecord> listForExport(Long deptId, String status) {
        if (status == null || status.isBlank()) {
            return jdbc.query(select() + " WHERE deleted = b'0' AND dept_id = ? ORDER BY id", this::map, deptId);
        }
        return jdbc.query(
                select() + " WHERE deleted = b'0' AND dept_id = ? AND status = ? ORDER BY id",
                this::map, deptId, status);
    }

    private String select() {
        return """
                SELECT id, member_code, douyin_id, douyin_id_raw, dept_id, subscribed_months, subscribed_days,
                       expire_at, start_at, available_count, unlocked_count, remain_count, status,
                       bind_state, bound_account_id, bound_at, remark, version, create_time
                  FROM kjx_member
                """;
    }

    private MemberRecord map(ResultSet rs, int rowNum) throws SQLException {
        Timestamp expire = rs.getTimestamp("expire_at");
        Timestamp start = rs.getTimestamp("start_at");
        Timestamp bound = rs.getTimestamp("bound_at");
        Timestamp created = rs.getTimestamp("create_time");
        long boundAccount = rs.getLong("bound_account_id");
        boolean boundNull = rs.wasNull();
        return new MemberRecord(
                rs.getLong("id"),
                rs.getString("member_code"),
                rs.getString("douyin_id"),
                rs.getString("douyin_id_raw"),
                rs.getLong("dept_id"),
                rs.getInt("subscribed_months"),
                rs.getInt("subscribed_days"),
                expire == null ? null : expire.toLocalDateTime(),
                start == null ? null : start.toLocalDateTime(),
                rs.getInt("available_count"),
                rs.getInt("unlocked_count"),
                rs.getInt("remain_count"),
                rs.getString("status"),
                rs.getInt("bind_state"),
                boundNull ? null : boundAccount,
                bound == null ? null : bound.toLocalDateTime(),
                rs.getString("remark"),
                rs.getInt("version"),
                created == null ? null : created.toLocalDateTime());
    }
}
