package com.koujunxin.work.jdbc;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.koujunxin.work.catalog.AccessForm;
import com.koujunxin.work.catalog.AdminWorkQuery;
import com.koujunxin.work.catalog.DuplicateWorkCodeException;
import com.koujunxin.work.catalog.WorkRecord;
import com.koujunxin.work.catalog.WorkRepository;
import com.koujunxin.work.catalog.WorkStatus;
import com.koujunxin.work.catalog.WorkType;

public class JdbcWorkRepository implements WorkRepository {

    private static final String SELECT_COLUMNS = """
            id, work_code, CONVERT(name USING utf8mb4) AS name, work_type,
            CONVERT(summary USING utf8mb4) AS summary, cover_url,
            CAST(screenshots AS CHAR CHARACTER SET utf8mb4) AS screenshots,
            category, tags, status, current_version, content_version, access_form, prompt_mode,
            allowed_channels, allow_byok_direct, CAST(apps AS CHAR CHARACTER SET utf8mb4) AS apps,
            CAST(tools AS CHAR CHARACTER SET utf8mb4) AS tools,
            CAST(server_tools AS CHAR CHARACTER SET utf8mb4) AS server_tools, pricing_mode, gray_ratio, sort_weight, unlock_count,
            publish_time, creator, create_time, updater, update_time, deleted
            """;

    private final JdbcTemplate jdbcTemplate;
    private final JsonColumns jsonColumns;

    public JdbcWorkRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.jsonColumns = new JsonColumns(objectMapper);
    }

    @Override
    public WorkRecord insert(WorkRecord record) {
        KeyHolder keys = new GeneratedKeyHolder();
        try {
            jdbcTemplate.update(con -> {
                PreparedStatement ps = con.prepareStatement("""
                        INSERT INTO kjx_work (
                          work_code, name, work_type, summary, cover_url, screenshots, category, tags,
                          status, current_version, content_version, access_form, prompt_mode, allowed_channels,
                          allow_byok_direct, apps, tools, server_tools, pricing_mode, gray_ratio, sort_weight,
                          unlock_count, publish_time, creator, updater, deleted, tenant_id
                        ) VALUES (
                          ?, ?, ?, ?, ?, CAST(? AS JSON), ?, ?,
                          ?, ?, ?, ?, ?, ?,
                          ?, CAST(? AS JSON), CAST(? AS JSON), CAST(? AS JSON), ?, ?, ?,
                          ?, ?, ?, ?, b'0', 0)
                        """, Statement.RETURN_GENERATED_KEYS);
                bindInsert(ps, record);
                return ps;
            }, keys);
        } catch (DuplicateKeyException ex) {
            throw new DuplicateWorkCodeException(record.workCode());
        }
        Number id = keys.getKey();
        if (id == null) {
            throw new IllegalStateException("insert work did not return id");
        }
        return findById(id.longValue()).orElseThrow();
    }

    @Override
    public Optional<WorkRecord> findById(long id) {
        List<WorkRecord> rows = jdbcTemplate.query(
                "SELECT " + SELECT_COLUMNS + " FROM kjx_work WHERE id = ? AND deleted = b'0'",
                this::mapRow, id);
        return rows.stream().findFirst();
    }

    @Override
    public Optional<WorkRecord> findByCode(String workCode) {
        List<WorkRecord> rows = jdbcTemplate.query(
                "SELECT " + SELECT_COLUMNS + " FROM kjx_work WHERE work_code = ? AND deleted = b'0'",
                this::mapRow, workCode);
        return rows.stream().findFirst();
    }

    @Override
    public int updateCas(WorkRecord current, WorkRecord next) {
        return jdbcTemplate.update("""
                UPDATE kjx_work SET
                  name = ?, summary = ?, cover_url = ?, screenshots = CAST(? AS JSON), category = ?, tags = ?,
                  status = ?, current_version = ?, content_version = ?, access_form = ?, prompt_mode = ?,
                  allowed_channels = ?, allow_byok_direct = ?, apps = CAST(? AS JSON), tools = CAST(? AS JSON),
                  server_tools = CAST(? AS JSON), pricing_mode = ?, gray_ratio = ?, sort_weight = ?,
                  publish_time = ?, updater = ?
                WHERE id = ? AND deleted = b'0' AND status = ? AND update_time = ?
                """,
                next.name(),
                next.summary(),
                next.coverUrl(),
                jsonColumns.writeList(next.screenshots()),
                next.category(),
                JsonColumns.joinCsv(next.tags()),
                next.status().name(),
                next.currentVersion(),
                next.contentVersion(),
                next.accessForm().name(),
                next.promptMode(),
                JsonColumns.joinCsv(next.allowedChannels()),
                next.allowByokDirect(),
                jsonColumns.writeList(next.apps()),
                jsonColumns.writeList(next.tools()),
                jsonColumns.writeList(next.serverTools()),
                next.pricingMode(),
                next.grayRatio(),
                next.sortWeight(),
                timestamp(next.publishTime()),
                next.updater(),
                current.id(),
                current.status().name(),
                timestamp(current.updateTime()));
    }

    @Override
    public List<WorkRecord> listAdmin(AdminWorkQuery query) {
        StringBuilder sql = new StringBuilder("SELECT ").append(SELECT_COLUMNS)
                .append(" FROM kjx_work WHERE deleted = b'0'");
        List<Object> args = new ArrayList<>();
        appendAdminFilters(sql, args, query);
        sql.append(" ORDER BY sort_weight DESC, id DESC LIMIT ? OFFSET ?");
        args.add(query.pageSize());
        args.add(query.offset());
        return jdbcTemplate.query(sql.toString(), this::mapRow, args.toArray());
    }

    @Override
    public long countAdmin(AdminWorkQuery query) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM kjx_work WHERE deleted = b'0'");
        List<Object> args = new ArrayList<>();
        appendAdminFilters(sql, args, query);
        Long count = jdbcTemplate.queryForObject(sql.toString(), Long.class, args.toArray());
        return count == null ? 0 : count;
    }

    @Override
    public List<WorkRecord> listByStatuses(WorkStatus... statuses) {
        if (statuses == null || statuses.length == 0) {
            return List.of();
        }
        String placeholders = String.join(",", Collections.nCopies(statuses.length, "?"));
        Object[] args = new Object[statuses.length];
        for (int i = 0; i < statuses.length; i++) {
            args[i] = statuses[i].name();
        }
        return jdbcTemplate.query(
                "SELECT " + SELECT_COLUMNS + " FROM kjx_work WHERE deleted = b'0' AND status IN ("
                        + placeholders + ") ORDER BY sort_weight DESC, publish_time DESC, id DESC",
                this::mapRow, args);
    }

    @Override
    public List<WorkRecord> listByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<Long> ordered = List.copyOf(ids);
        String placeholders = String.join(",", Collections.nCopies(ordered.size(), "?"));
        return jdbcTemplate.query(
                "SELECT " + SELECT_COLUMNS + " FROM kjx_work WHERE deleted = b'0' AND id IN ("
                        + placeholders + ")",
                this::mapRow, ordered.toArray());
    }

    private static void appendAdminFilters(StringBuilder sql, List<Object> args, AdminWorkQuery query) {
        if (query.workType() != null) {
            sql.append(" AND work_type = ?");
            args.add(query.workType().name());
        }
        if (query.status() != null) {
            sql.append(" AND status = ?");
            args.add(query.status().name());
        }
        if (query.keyword() != null) {
            sql.append(" AND (name LIKE ? OR work_code LIKE ?)");
            String like = "%" + query.keyword() + "%";
            args.add(like);
            args.add(like);
        }
    }

    private void bindInsert(PreparedStatement ps, WorkRecord record) throws SQLException {
        int i = 1;
        ps.setString(i++, record.workCode());
        ps.setString(i++, record.name());
        ps.setString(i++, record.workType().name());
        ps.setString(i++, record.summary());
        ps.setString(i++, record.coverUrl());
        ps.setString(i++, jsonColumns.writeList(record.screenshots()));
        ps.setString(i++, record.category());
        ps.setString(i++, JsonColumns.joinCsv(record.tags()));
        ps.setString(i++, record.status().name());
        ps.setString(i++, record.currentVersion());
        ps.setString(i++, record.contentVersion());
        ps.setString(i++, record.accessForm().name());
        ps.setString(i++, record.promptMode());
        ps.setString(i++, JsonColumns.joinCsv(record.allowedChannels()));
        ps.setBoolean(i++, record.allowByokDirect());
        ps.setString(i++, jsonColumns.writeList(record.apps()));
        ps.setString(i++, jsonColumns.writeList(record.tools()));
        ps.setString(i++, jsonColumns.writeList(record.serverTools()));
        ps.setString(i++, record.pricingMode());
        ps.setInt(i++, record.grayRatio());
        ps.setInt(i++, record.sortWeight());
        ps.setInt(i++, record.unlockCount());
        ps.setTimestamp(i++, timestamp(record.publishTime()));
        ps.setString(i++, nullToEmpty(record.creator()));
        ps.setString(i++, nullToEmpty(record.updater()));
    }

    private WorkRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new WorkRecord(
                rs.getLong("id"),
                rs.getString("work_code"),
                rs.getString("name"),
                WorkType.from(rs.getString("work_type")),
                rs.getString("summary"),
                rs.getString("cover_url"),
                jsonColumns.readList(rs.getString("screenshots")),
                rs.getString("category"),
                JsonColumns.splitCsv(rs.getString("tags")),
                WorkStatus.from(rs.getString("status")),
                rs.getString("current_version"),
                rs.getString("content_version"),
                AccessForm.from(rs.getString("access_form")),
                rs.getString("prompt_mode"),
                JsonColumns.splitCsv(rs.getString("allowed_channels")),
                rs.getBoolean("allow_byok_direct"),
                jsonColumns.readList(rs.getString("apps")),
                jsonColumns.readList(rs.getString("tools")),
                jsonColumns.readList(rs.getString("server_tools")),
                rs.getString("pricing_mode"),
                rs.getInt("gray_ratio"),
                rs.getInt("sort_weight"),
                rs.getInt("unlock_count"),
                instant(rs.getTimestamp("publish_time")),
                rs.getString("creator"),
                instant(rs.getTimestamp("create_time")),
                rs.getString("updater"),
                instant(rs.getTimestamp("update_time")),
                rs.getBoolean("deleted"));
    }

    private static Timestamp timestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private static Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
