package com.koujunxin.tool.jdbc;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.koujunxin.tool.schema.ToolJsonSchema;
import com.koujunxin.tool.ui.UiNodeWhitelist;

public final class JdbcUiNodeTypes {

    private JdbcUiNodeTypes() {
    }

    public static UiNodeWhitelist load(JdbcTemplate jdbc, ObjectMapper mapper) {
        Map<String, JsonNode> schemas = new LinkedHashMap<>();
        jdbc.query(
                "SELECT type, props_schema FROM kjx_ui_node_type WHERE enabled = 1",
                rs -> {
                    try {
                        schemas.put(rs.getString("type"), mapper.readTree(rs.getString("props_schema")));
                    } catch (JsonProcessingException ex) {
                        throw new IllegalStateException("invalid ui props schema", ex);
                    }
                });
        return new UiNodeWhitelist(schemas, new ToolJsonSchema(), mapper);
    }
}
