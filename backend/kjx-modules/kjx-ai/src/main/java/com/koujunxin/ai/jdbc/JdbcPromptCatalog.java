package com.koujunxin.ai.jdbc;

import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import com.koujunxin.ai.api.AiException;
import com.koujunxin.ai.api.port.MethodPromptStore;
import com.koujunxin.ai.api.port.PromptCatalog;
import com.koujunxin.ai.prompt.PromptKind;
import com.koujunxin.ai.prompt.PromptRecord;
import com.koujunxin.ai.prompt.PromptStatus;

public final class JdbcPromptCatalog implements PromptCatalog, MethodPromptStore {

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;

    public JdbcPromptCatalog(JdbcTemplate jdbc, TransactionTemplate tx) {
        this.jdbc = jdbc;
        this.tx = tx;
    }

    @Override
    public PromptRecord saveWork(PromptRecord record) {
        if (record.kind() != PromptKind.WORK) {
            throw AiException.rejected("WORK catalog rejects METHOD prompt");
        }
        return save(record);
    }

    @Override
    public void saveMethod(PromptRecord record) {
        if (record.kind() != PromptKind.METHOD) {
            throw AiException.rejected("METHOD store rejects WORK prompt");
        }
        save(record);
    }

    @Override
    public void activateWork(String workCode, String version) {
        activate(workCode, PromptKind.WORK, version);
    }

    @Override
    public void activateMethod(String workCode, String version) {
        activate(workCode, PromptKind.METHOD, version);
    }

    @Override
    public Optional<PromptRecord> findActiveWork(String workCode) {
        List<PromptRecord> rows = jdbc.query(
                """
                SELECT work_code, kind, prompt_mode, version, status, stable_prefix, tool_spec, variable_schema
                  FROM kjx_prompt_template
                 WHERE work_code = ? AND kind = 'WORK' AND status = 'ACTIVE'
                 LIMIT 1
                """,
                (rs, rowNum) -> map(rs),
                workCode);
        return rows.stream().findFirst();
    }

    @Override
    public Optional<PromptRecord> findWork(String workCode, String version) {
        List<PromptRecord> rows = jdbc.query(
                """
                SELECT work_code, kind, prompt_mode, version, status, stable_prefix, tool_spec, variable_schema
                  FROM kjx_prompt_template
                 WHERE work_code = ? AND kind = 'WORK' AND version = ?
                 LIMIT 1
                """,
                (rs, rowNum) -> map(rs),
                workCode,
                version);
        return rows.stream().findFirst();
    }

    private PromptRecord save(PromptRecord record) {
        jdbc.update(
                """
                INSERT INTO kjx_prompt_template
                  (work_code, kind, prompt_mode, version, status, stable_prefix, tool_spec, variable_schema)
                VALUES (?, ?, ?, ?, ?, ?, CAST(? AS JSON), CAST(? AS JSON))
                ON DUPLICATE KEY UPDATE
                  prompt_mode = VALUES(prompt_mode),
                  status = VALUES(status),
                  stable_prefix = VALUES(stable_prefix),
                  tool_spec = VALUES(tool_spec),
                  variable_schema = VALUES(variable_schema)
                """,
                record.workCode(),
                record.kind().name(),
                record.promptMode(),
                record.version(),
                record.status().name(),
                record.stablePrefix(),
                record.toolSpecJson(),
                record.variableSchemaJson());
        return record;
    }

    private void activate(String workCode, PromptKind kind, String version) {
        tx.executeWithoutResult(status -> {
            jdbc.update(
                    "UPDATE kjx_prompt_template SET status = 'ARCHIVED' WHERE work_code = ? AND kind = ? AND status = 'ACTIVE'",
                    workCode,
                    kind.name());
            int updated = jdbc.update(
                    "UPDATE kjx_prompt_template SET status = 'ACTIVE' WHERE work_code = ? AND kind = ? AND version = ?",
                    workCode,
                    kind.name(),
                    version);
            if (updated != 1) {
                throw AiException.rejected("prompt version not found");
            }
        });
    }

    private static PromptRecord map(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new PromptRecord(
                rs.getString("work_code"),
                PromptKind.valueOf(rs.getString("kind")),
                rs.getString("prompt_mode"),
                rs.getString("version"),
                PromptStatus.valueOf(rs.getString("status")),
                rs.getString("stable_prefix"),
                rs.getString("tool_spec"),
                rs.getString("variable_schema"));
    }
}
