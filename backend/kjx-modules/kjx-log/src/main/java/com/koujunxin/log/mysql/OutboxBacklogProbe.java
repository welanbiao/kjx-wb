package com.koujunxin.log.mysql;

import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.jdbc.core.JdbcTemplate;

import com.koujunxin.log.api.port.ObservabilityMetricsPort;

/**
 * 从 MySQL Outbox 刷新积压/DEAD 指标。查询失败不得抛到业务线程。
 */
public final class OutboxBacklogProbe {

    private final JdbcTemplate jdbcTemplate;
    private final ObservabilityMetricsPort metrics;
    private final AtomicBoolean warned = new AtomicBoolean();

    public OutboxBacklogProbe(JdbcTemplate jdbcTemplate, ObservabilityMetricsPort metrics) {
        this.jdbcTemplate = jdbcTemplate;
        this.metrics = metrics;
    }

    public void refresh() {
        try {
            Long pending = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM kjx_outbox_event WHERE status IN ('PENDING', 'PROCESSING')",
                    Long.class);
            Long dead = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM kjx_outbox_event WHERE status = 'DEAD'",
                    Long.class);
            metrics.recordOutboxBacklog(pending == null ? 0 : pending);
            metrics.recordOutboxDead(dead == null ? 0 : dead);
        } catch (RuntimeException ex) {
            if (warned.compareAndSet(false, true)) {
                // 表尚未迁移时静默；不阻断启动。
            }
        }
    }
}
