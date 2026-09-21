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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.koujunxin.member.domain.AppealRecord;
import com.koujunxin.member.domain.PageResult;

public class AppealRepository {

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public AppealRepository(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    public long insert(AppealRecord row) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    """
                    INSERT INTO kjx_binding_appeal
                      (account_id, binding_id, appeal_type, content, contact, attachments, status)
                    VALUES (?, ?, ?, ?, ?, CAST(? AS JSON), 'PENDING')
                    """,
                    Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, row.accountId());
            if (row.bindingId() == null) {
                ps.setObject(2, null);
            } else {
                ps.setLong(2, row.bindingId());
            }
            ps.setString(3, row.appealType());
            ps.setString(4, row.content());
            ps.setString(5, row.contact());
            ps.setString(6, json(row.attachments()));
            return ps;
        }, keys);
        Number id = keys.getKey();
        if (id == null) {
            throw new IllegalStateException("appeal insert produced no id");
        }
        return id.longValue();
    }

    public Optional<AppealRecord> findById(long id) {
        return jdbc.query(select() + " WHERE id = ?", this::map, id).stream().findFirst();
    }

    public Optional<AppealRecord> lockById(long id) {
        return jdbc.query(select() + " WHERE id = ? FOR UPDATE", this::map, id).stream().findFirst();
    }

    public int casHandle(long id, String result, long handlerId, LocalDateTime handleTime, String handleNote) {
        return jdbc.update(
                """
                UPDATE kjx_binding_appeal
                   SET status = ?, handler_id = ?, handle_time = ?, handle_note = ?
                 WHERE id = ? AND status = 'PENDING'
                """,
                result, handlerId, Timestamp.valueOf(handleTime), handleNote, id);
    }

    public PageResult<AppealRecord> page(String status, int pageNo, int pageSize) {
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (status != null && !status.isBlank()) {
            where.append(" AND status = ?");
            args.add(status);
        }
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM kjx_binding_appeal" + where, Long.class, args.toArray());
        int offset = Math.max(0, (pageNo - 1) * pageSize);
        args.add(pageSize);
        args.add(offset);
        List<AppealRecord> list = jdbc.query(
                select() + where + " ORDER BY create_time DESC LIMIT ? OFFSET ?",
                this::map, args.toArray());
        return new PageResult<>(list, total == null ? 0 : total);
    }

    private String select() {
        return """
                SELECT id, account_id, binding_id, appeal_type, content, contact, attachments, status,
                       handler_id, handle_time, handle_note, create_time
                  FROM kjx_binding_appeal
                """;
    }

    private AppealRecord map(ResultSet rs, int rowNum) throws SQLException {
        Timestamp handled = rs.getTimestamp("handle_time");
        Timestamp created = rs.getTimestamp("create_time");
        long binding = rs.getLong("binding_id");
        boolean bindingNull = rs.wasNull();
        long handler = rs.getLong("handler_id");
        boolean handlerNull = rs.wasNull();
        return new AppealRecord(
                rs.getLong("id"),
                rs.getLong("account_id"),
                bindingNull ? null : binding,
                rs.getString("appeal_type"),
                rs.getString("content"),
                rs.getString("contact"),
                readList(rs.getString("attachments")),
                rs.getString("status"),
                handlerNull ? null : handler,
                handled == null ? null : handled.toLocalDateTime(),
                rs.getString("handle_note"),
                created == null ? null : created.toLocalDateTime());
    }

    private String json(List<String> attachments) {
        try {
            return mapper.writeValueAsString(attachments == null ? List.of() : attachments);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("attachments json failed", ex);
        }
    }

    private List<String> readList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return mapper.readValue(json, new TypeReference<>() {
            });
        } catch (JsonProcessingException ex) {
            return List.of();
        }
    }
}
