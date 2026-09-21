package com.koujunxin.member.jdbc;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

import com.koujunxin.member.domain.PageResult;
import com.koujunxin.member.domain.UnlockRecordRow;

public class UnlockRecordRepository {

    private final JdbcTemplate jdbc;

    public UnlockRecordRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public long insert(UnlockRecordRow row) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    """
                    INSERT INTO kjx_unlock_record
                      (dept_id, member_id, member_code, douyin_id, account_id, work_id, work_name, work_version,
                       unlock_time, before_unlocked_count, after_unlocked_count, before_remain_count, after_remain_count,
                       result, fail_reason, source, operator_id, request_fingerprint, idempotency_key)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    Statement.RETURN_GENERATED_KEYS);
            int i = 1;
            ps.setLong(i++, row.deptId());
            ps.setLong(i++, row.memberId());
            ps.setString(i++, row.memberCode());
            ps.setString(i++, row.douyinId());
            if (row.accountId() == null) {
                ps.setNull(i++, Types.BIGINT);
            } else {
                ps.setLong(i++, row.accountId());
            }
            ps.setLong(i++, row.workId());
            ps.setString(i++, row.workName());
            ps.setString(i++, row.workVersion());
            ps.setTimestamp(i++, Timestamp.valueOf(row.unlockTime()));
            ps.setInt(i++, row.beforeUnlockedCount());
            ps.setInt(i++, row.afterUnlockedCount());
            ps.setInt(i++, row.beforeRemainCount());
            ps.setInt(i++, row.afterRemainCount());
            ps.setString(i++, row.result());
            ps.setString(i++, row.failReason());
            ps.setString(i++, row.source());
            if (row.operatorId() == null) {
                ps.setNull(i++, Types.BIGINT);
            } else {
                ps.setLong(i++, row.operatorId());
            }
            ps.setString(i++, row.requestFingerprint());
            ps.setString(i++, row.idempotencyKey());
            return ps;
        }, keys);
        Number id = keys.getKey();
        if (id == null) {
            throw new IllegalStateException("unlock_record insert produced no id");
        }
        return id.longValue();
    }

    public Optional<UnlockRecordRow> findById(long id) {
        return jdbc.query(select() + " WHERE id = ?", this::map, id).stream().findFirst();
    }

    public PageResult<UnlockRecordRow> page(Long deptId, Long memberId, Long workId, String result, String source,
                                            int pageNo, int pageSize) {
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (deptId != null) {
            where.append(" AND dept_id = ?");
            args.add(deptId);
        }
        if (memberId != null) {
            where.append(" AND member_id = ?");
            args.add(memberId);
        }
        if (workId != null) {
            where.append(" AND work_id = ?");
            args.add(workId);
        }
        if (result != null && !result.isBlank()) {
            where.append(" AND result = ?");
            args.add(result);
        }
        if (source != null && !source.isBlank()) {
            where.append(" AND source = ?");
            args.add(source);
        }
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM kjx_unlock_record" + where, Long.class, args.toArray());
        int offset = Math.max(0, (pageNo - 1) * pageSize);
        args.add(pageSize);
        args.add(offset);
        List<UnlockRecordRow> list = jdbc.query(
                select() + where + " ORDER BY unlock_time DESC, id DESC LIMIT ? OFFSET ?",
                this::map, args.toArray());
        return new PageResult<>(list, total == null ? 0 : total);
    }

    public List<UnlockRecordRow> listForExport(Long deptId, Long memberId, String result, String source) {
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (deptId != null) {
            where.append(" AND dept_id = ?");
            args.add(deptId);
        }
        if (memberId != null) {
            where.append(" AND member_id = ?");
            args.add(memberId);
        }
        if (result != null && !result.isBlank()) {
            where.append(" AND result = ?");
            args.add(result);
        }
        if (source != null && !source.isBlank()) {
            where.append(" AND source = ?");
            args.add(source);
        }
        return jdbc.query(select() + where + " ORDER BY unlock_time DESC, id DESC", this::map, args.toArray());
    }

    private String select() {
        return """
                SELECT id, dept_id, member_id, member_code, douyin_id, account_id, work_id, work_name, work_version,
                       unlock_time, before_unlocked_count, after_unlocked_count, before_remain_count, after_remain_count,
                       result, fail_reason, source, operator_id, request_fingerprint, idempotency_key
                  FROM kjx_unlock_record
                """;
    }

    private UnlockRecordRow map(ResultSet rs, int rowNum) throws SQLException {
        long account = rs.getLong("account_id");
        boolean accountNull = rs.wasNull();
        long operator = rs.getLong("operator_id");
        boolean operatorNull = rs.wasNull();
        Timestamp time = rs.getTimestamp("unlock_time");
        return new UnlockRecordRow(
                rs.getLong("id"),
                rs.getLong("dept_id"),
                rs.getLong("member_id"),
                rs.getString("member_code"),
                rs.getString("douyin_id"),
                accountNull ? null : account,
                rs.getLong("work_id"),
                rs.getString("work_name"),
                rs.getString("work_version"),
                time == null ? null : time.toLocalDateTime(),
                rs.getInt("before_unlocked_count"),
                rs.getInt("after_unlocked_count"),
                rs.getInt("before_remain_count"),
                rs.getInt("after_remain_count"),
                rs.getString("result"),
                rs.getString("fail_reason"),
                rs.getString("source"),
                operatorNull ? null : operator,
                rs.getString("request_fingerprint"),
                rs.getString("idempotency_key"));
    }
}
