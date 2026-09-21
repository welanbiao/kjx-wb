package com.koujunxin.log.projection;

import java.sql.Timestamp;
import java.time.Instant;

import org.springframework.jdbc.core.JdbcTemplate;

import com.koujunxin.log.api.port.ObservabilityMetricsPort;
import com.koujunxin.log.api.port.ProjectionReconcilePort;
import com.koujunxin.log.redact.LogRedactor;

public final class JdbcProjectionReconciler implements ProjectionReconcilePort {

    private static final String INSERT_SQL = """
            INSERT INTO log_projection_reconcile
              (window_start, window_end, category, mysql_published, receipt_done, clickhouse_rows, status, detail)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private final JdbcTemplate jdbcTemplate;
    private final ClickHouseProjectionSink sink;
    private final ObservabilityMetricsPort metrics;
    private final LogRedactor redactor;

    public JdbcProjectionReconciler(
            JdbcTemplate jdbcTemplate,
            ClickHouseProjectionSink sink,
            ObservabilityMetricsPort metrics,
            LogRedactor redactor) {
        this.jdbcTemplate = jdbcTemplate;
        this.sink = sink;
        this.metrics = metrics;
        this.redactor = redactor;
    }

    @Override
    public ReconcileResult reconcile(String category, Instant windowStart, Instant windowEnd) {
        String eventType = eventType(category);
        String channel = ExternalProjectionKeys.channelForEventType(eventType);
        Timestamp from = Timestamp.from(windowStart);
        Timestamp to = Timestamp.from(windowEnd);
        Long published = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*) FROM kjx_outbox_event
                         WHERE event_type = ? AND status = 'PUBLISHED'
                           AND create_time >= ? AND create_time < ?
                        """,
                Long.class, eventType, from, to);
        Long receipts = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*) FROM kjx_outbox_consume_receipt
                         WHERE channel = ? AND status = 'DONE'
                           AND create_time >= ? AND create_time < ?
                        """,
                Long.class, channel, from, to);
        long mysqlPublished = published == null ? 0 : published;
        long receiptDone = receipts == null ? 0 : receipts;
        Long clickhouseRows = null;
        String status;
        String detail = null;
        try {
            clickhouseRows = sink.count(ClickHouseProjectionSink.tableFor(eventType));
            if (mysqlPublished == receiptDone && clickhouseRows != null && clickhouseRows >= receiptDone) {
                status = "MATCHED";
            } else {
                status = "DRIFT";
                detail = "mysql=" + mysqlPublished + " receipt=" + receiptDone + " ch=" + clickhouseRows;
            }
        } catch (RuntimeException ex) {
            metrics.recordProjectionError();
            status = "CH_UNAVAILABLE";
            detail = redactor.redactText(ex.getMessage());
        }
        jdbcTemplate.update(INSERT_SQL, from, to, category, mysqlPublished, receiptDone, clickhouseRows, status, detail);
        return new ReconcileResult(category, mysqlPublished, receiptDone, clickhouseRows, status, detail);
    }

    private static String eventType(String category) {
        if (category == null) {
            throw new IllegalArgumentException("category is required");
        }
        return switch (category.toUpperCase()) {
            case "ACCESS" -> "analytics.access.projected";
            case "AI_CALL" -> "analytics.ai_call.projected";
            case "TOOL_CALL" -> "analytics.tool_call.projected";
            default -> throw new IllegalArgumentException("unsupported reconcile category: " + category);
        };
    }
}
