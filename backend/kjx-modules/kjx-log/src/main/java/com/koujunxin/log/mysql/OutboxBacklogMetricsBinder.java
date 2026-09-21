package com.koujunxin.log.mysql;

import org.springframework.jdbc.core.JdbcTemplate;

import com.koujunxin.log.metrics.MicrometerObservabilityMetrics;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;

/**
 * Prometheus 刮取时查 MySQL Outbox 积压。查询失败返回 0，不抛到刮取线程之外。
 */
public final class OutboxBacklogMetricsBinder implements MeterBinder {

    private final JdbcTemplate jdbcTemplate;

    public OutboxBacklogMetricsBinder(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        Gauge.builder(MicrometerObservabilityMetrics.OUTBOX_BACKLOG, this, OutboxBacklogMetricsBinder::pending)
                .description("PENDING+PROCESSING outbox rows")
                .register(registry);
        Gauge.builder(MicrometerObservabilityMetrics.OUTBOX_DEAD, this, OutboxBacklogMetricsBinder::dead)
                .description("DEAD outbox rows")
                .register(registry);
    }

    private double pending() {
        return count("SELECT COUNT(*) FROM kjx_outbox_event WHERE status IN ('PENDING', 'PROCESSING')");
    }

    private double dead() {
        return count("SELECT COUNT(*) FROM kjx_outbox_event WHERE status = 'DEAD'");
    }

    private double count(String sql) {
        try {
            Long value = jdbcTemplate.queryForObject(sql, Long.class);
            return value == null ? 0 : value;
        } catch (RuntimeException ex) {
            return 0;
        }
    }
}
