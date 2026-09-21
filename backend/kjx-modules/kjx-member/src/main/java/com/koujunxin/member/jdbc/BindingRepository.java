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

import com.koujunxin.member.domain.BindingRecord;
import com.koujunxin.member.domain.PageResult;

public class BindingRepository {

    private final JdbcTemplate jdbc;

    public BindingRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public long insert(BindingRecord row, byte[] submitIp, String submitDevice) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    """
                    INSERT INTO kjx_member_binding
                      (dept_id, account_id, member_id, douyin_id_input, douyin_id_raw, member_code_input,
                       claim_months, status, submit_ip, submit_device)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, row.deptId());
            ps.setLong(2, row.accountId());
            ps.setLong(3, row.memberId());
            ps.setString(4, row.douyinIdInput());
            ps.setString(5, row.douyinIdRaw());
            ps.setString(6, row.memberCodeInput());
            ps.setInt(7, row.claimMonths());
            ps.setString(8, row.status());
            ps.setBytes(9, submitIp);
            ps.setString(10, submitDevice);
            return ps;
        }, keys);
        Number id = keys.getKey();
        if (id == null) {
            throw new IllegalStateException("binding insert produced no id");
        }
        return id.longValue();
    }

    public void updatePending(long id, BindingRecord row, byte[] submitIp, String submitDevice) {
        jdbc.update(
                """
                UPDATE kjx_member_binding
                   SET dept_id = ?, member_id = ?, douyin_id_input = ?, douyin_id_raw = ?,
                       member_code_input = ?, claim_months = ?, status = ?, submit_ip = ?,
                       submit_device = ?, reject_reason = NULL, matched_at = NULL,
                       reviewer_id = NULL, review_time = NULL
                 WHERE id = ?
                """,
                row.deptId(), row.memberId(), row.douyinIdInput(), row.douyinIdRaw(),
                row.memberCodeInput(), row.claimMonths(), row.status(), submitIp, submitDevice, id);
    }

    public Optional<BindingRecord> findById(long id) {
        return jdbc.query(select() + " WHERE id = ?", this::map, id).stream().findFirst();
    }

    public Optional<BindingRecord> lockById(long id) {
        return jdbc.query(select() + " WHERE id = ? FOR UPDATE", this::map, id).stream().findFirst();
    }

    public Optional<BindingRecord> findByAccountId(long accountId) {
        return jdbc.query(select() + " WHERE account_id = ?", this::map, accountId).stream().findFirst();
    }

    public Optional<BindingRecord> findByMemberId(long memberId) {
        return jdbc.query(select() + " WHERE member_id = ?", this::map, memberId).stream().findFirst();
    }

    public int casStatus(long id, String from, String to, Long reviewerId, LocalDateTime reviewTime,
                         String rejectReason, LocalDateTime matchedAt) {
        return jdbc.update(
                """
                UPDATE kjx_member_binding
                   SET status = ?, reviewer_id = ?, review_time = ?, reject_reason = ?, matched_at = ?
                 WHERE id = ? AND status = ?
                """,
                to, reviewerId, reviewTime == null ? null : Timestamp.valueOf(reviewTime),
                rejectReason, matchedAt == null ? null : Timestamp.valueOf(matchedAt), id, from);
    }

    public int markUnbinding(long id, LocalDateTime applyAt) {
        return jdbc.update(
                """
                UPDATE kjx_member_binding
                   SET status = 'UNBINDING', unbind_apply_at = ?
                 WHERE id = ? AND status = 'MATCHED'
                """,
                Timestamp.valueOf(applyAt), id);
    }

    public void deleteById(long id) {
        jdbc.update("DELETE FROM kjx_member_binding WHERE id = ?", id);
    }

    public void deleteRejectedByMemberExcept(long memberId, long keepAccountId) {
        jdbc.update(
                """
                DELETE FROM kjx_member_binding
                 WHERE member_id = ? AND account_id <> ? AND status = 'REJECTED'
                """,
                memberId, keepAccountId);
    }

    public PageResult<BindingRecord> page(Long deptId, String status, String memberCode, int pageNo, int pageSize) {
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (deptId != null) {
            where.append(" AND b.dept_id = ?");
            args.add(deptId);
        }
        if (status != null && !status.isBlank()) {
            where.append(" AND b.status = ?");
            args.add(status);
        }
        if (memberCode != null && !memberCode.isBlank()) {
            where.append(" AND REPLACE(m.member_code, '-', '') = ?");
            args.add(memberCode.replace(" ", "").replace("-", "").toUpperCase());
        }
        Long total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM kjx_member_binding b LEFT JOIN kjx_member m ON m.id = b.member_id" + where,
                Long.class, args.toArray());
        int offset = Math.max(0, (pageNo - 1) * pageSize);
        args.add(pageSize);
        args.add(offset);
        List<BindingRecord> list = jdbc.query(
                select("b") + " LEFT JOIN kjx_member m ON m.id = b.member_id" + where
                        + " ORDER BY b.create_time DESC LIMIT ? OFFSET ?",
                this::map, args.toArray());
        return new PageResult<>(list, total == null ? 0 : total);
    }

    private String select() {
        return select("kjx_member_binding");
    }

    private String select(String alias) {
        return "SELECT " + alias + ".id, " + alias + ".dept_id, " + alias + ".account_id, " + alias + ".member_id, "
                + alias + ".douyin_id_input, " + alias + ".douyin_id_raw, " + alias + ".member_code_input, "
                + alias + ".claim_months, " + alias + ".status, " + alias + ".matched_at, " + alias + ".reviewer_id, "
                + alias + ".review_time, " + alias + ".reject_reason, " + alias + ".unbind_apply_at, "
                + alias + ".unbind_at, " + alias + ".create_time FROM kjx_member_binding " + alias;
    }

    private BindingRecord map(ResultSet rs, int rowNum) throws SQLException {
        Timestamp matched = rs.getTimestamp("matched_at");
        Timestamp review = rs.getTimestamp("review_time");
        Timestamp unbindApply = rs.getTimestamp("unbind_apply_at");
        Timestamp unbindAt = rs.getTimestamp("unbind_at");
        Timestamp created = rs.getTimestamp("create_time");
        long reviewer = rs.getLong("reviewer_id");
        boolean reviewerNull = rs.wasNull();
        return new BindingRecord(
                rs.getLong("id"),
                rs.getLong("dept_id"),
                rs.getLong("account_id"),
                rs.getLong("member_id"),
                rs.getString("douyin_id_input"),
                rs.getString("douyin_id_raw"),
                rs.getString("member_code_input"),
                rs.getInt("claim_months"),
                rs.getString("status"),
                matched == null ? null : matched.toLocalDateTime(),
                reviewerNull ? null : reviewer,
                review == null ? null : review.toLocalDateTime(),
                rs.getString("reject_reason"),
                unbindApply == null ? null : unbindApply.toLocalDateTime(),
                unbindAt == null ? null : unbindAt.toLocalDateTime(),
                created == null ? null : created.toLocalDateTime());
    }
}
