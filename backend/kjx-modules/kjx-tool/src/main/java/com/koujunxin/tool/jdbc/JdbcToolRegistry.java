package com.koujunxin.tool.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.koujunxin.tool.api.RateLimitSpec;
import com.koujunxin.tool.api.ToolSpec;
import com.koujunxin.tool.json.JsonSupport;
import com.koujunxin.tool.registry.ToolCatalog;
import com.koujunxin.tool.registry.ToolRegistration;

public final class JdbcToolRegistry implements ToolCatalog {

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public JdbcToolRegistry(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    @Override
    public Optional<ToolSpec> find(String workCode, String toolCode, String version) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                    """
                    SELECT t.id, t.code, t.type, t.display_name, t.enabled AS tool_enabled,
                           v.version, v.description, v.parameters_schema, v.preconditions, v.implementation,
                           v.rate_limit, v.timeout_ms, v.retry_max, v.status,
                           b.enabled AS binding_enabled, b.tool_version AS binding_version
                    FROM kjx_tool t
                    JOIN kjx_tool_version v ON v.tool_id = t.id
                    LEFT JOIN kjx_tool_work_binding b ON b.tool_code = t.code AND b.work_code = ?
                    WHERE t.code = ? AND v.version = ?
                    """,
                    this::mapRow,
                    workCode,
                    toolCode,
                    version));
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    @Override
    public void register(ToolRegistration registration) {
        jdbc.update(
                """
                INSERT INTO kjx_tool (code, type, display_name, enabled)
                VALUES (?, ?, ?, 1)
                ON DUPLICATE KEY UPDATE display_name = VALUES(display_name), type = VALUES(type), enabled = 1
                """,
                registration.code(),
                registration.type(),
                registration.displayName());
        Long toolId = jdbc.queryForObject("SELECT id FROM kjx_tool WHERE code = ?", Long.class, registration.code());
        ObjectNode rate = mapper.createObjectNode();
        if (registration.rateLimit().perSession() != null) {
            rate.put("perSession", registration.rateLimit().perSession());
        }
        if (registration.rateLimit().perDay() != null) {
            rate.put("perDay", registration.rateLimit().perDay());
        }
        if (registration.rateLimit().cooldownSec() != null) {
            rate.put("cooldownSec", registration.rateLimit().cooldownSec());
        }
        String pre = registration.preconditions() == null ? "{}" : JsonSupport.write(mapper, registration.preconditions());
        jdbc.update(
                """
                INSERT INTO kjx_tool_version
                  (tool_id, version, description, parameters_schema, preconditions, implementation,
                   rate_limit, side_effects, audit, timeout_ms, retry_max, status)
                VALUES (?, ?, ?, CAST(? AS JSON), CAST(? AS JSON), CAST(? AS JSON), CAST(? AS JSON), CAST('[]' AS JSON), 1, ?, ?, 'ACTIVE')
                ON DUPLICATE KEY UPDATE
                  description = VALUES(description),
                  parameters_schema = VALUES(parameters_schema),
                  preconditions = VALUES(preconditions),
                  implementation = VALUES(implementation),
                  rate_limit = VALUES(rate_limit),
                  timeout_ms = VALUES(timeout_ms),
                  retry_max = VALUES(retry_max),
                  status = 'ACTIVE'
                """,
                toolId,
                registration.version(),
                registration.description(),
                JsonSupport.write(mapper, registration.parametersSchema()),
                pre,
                JsonSupport.write(mapper, registration.implementation()),
                JsonSupport.write(mapper, rate),
                registration.timeoutMs(),
                registration.retryMax());
    }

    @Override
    public void bindWork(String workCode, String toolCode, String version, boolean enabled) {
        jdbc.update(
                """
                INSERT INTO kjx_tool_work_binding (work_code, tool_code, tool_version, enabled)
                VALUES (?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE tool_version = VALUES(tool_version), enabled = VALUES(enabled)
                """,
                workCode,
                toolCode,
                version,
                enabled ? 1 : 0);
    }

    private ToolSpec mapRow(ResultSet rs, int rowNum) throws SQLException {
        boolean toolEnabled = rs.getBoolean("tool_enabled");
        String type = rs.getString("type");
        String status = rs.getString("status");
        int bindingFlag = rs.getInt("binding_enabled");
        boolean bindingWasNull = rs.wasNull();
        String bindingVersion = rs.getString("binding_version");
        boolean enabled = toolEnabled && "ACTIVE".equals(status);
        if ("custom".equalsIgnoreCase(type) && bindingWasNull) {
            enabled = false;
        }
        if (!bindingWasNull && bindingFlag == 0) {
            enabled = false;
        }
        if (bindingVersion != null && !bindingVersion.isBlank() && !bindingVersion.equals(rs.getString("version"))) {
            enabled = false;
        }
        JsonNode implementation = parseJson(rs.getString("implementation"));
        return new ToolSpec(
                rs.getLong("id"),
                rs.getString("code"),
                rs.getString("version"),
                type,
                rs.getString("display_name"),
                rs.getString("description"),
                parseJson(rs.getString("parameters_schema")),
                parseJson(rs.getString("preconditions")),
                implementation,
                RateLimitSpec.fromJson(parseJson(rs.getString("rate_limit"))),
                rs.getInt("timeout_ms"),
                rs.getInt("retry_max"),
                enabled);
    }

    private JsonNode parseJson(String raw) {
        if (raw == null || raw.isBlank()) {
            return mapper.createObjectNode();
        }
        try {
            return mapper.readTree(raw);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("invalid json column", ex);
        }
    }
}
