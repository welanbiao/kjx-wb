package com.koujunxin.tool.jdbc;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;

import com.koujunxin.tool.api.RateLimitSpec;
import com.koujunxin.tool.api.ToolFailure;
import com.koujunxin.tool.execute.RateLimitStore;

public final class JdbcRateLimitStore implements RateLimitStore {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private final JdbcTemplate jdbc;

    public JdbcRateLimitStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void checkAndIncrement(String sessionId, String toolCode, RateLimitSpec spec, Instant now) {
        if (spec == null || !spec.limited()) {
            return;
        }
        String dayBucket = "day:" + now.atZone(ZONE).toLocalDate();
        List<BucketRow> rows = jdbc.query(
                """
                SELECT bucket, count, last_called_at
                  FROM kjx_tool_rate_bucket
                 WHERE session_id = ? AND tool_code = ? AND bucket IN ('session', ?)
                 FOR UPDATE
                """,
                (rs, n) -> new BucketRow(
                        rs.getString("bucket"),
                        rs.getInt("count"),
                        toInstant(rs.getObject("last_called_at"))),
                sessionId,
                toolCode,
                dayBucket);
        int sessionCount = 0;
        int dayCount = 0;
        Instant lastCalled = Instant.EPOCH;
        for (BucketRow row : rows) {
            if ("session".equals(row.bucket())) {
                sessionCount = row.count();
                lastCalled = row.lastCalled();
            } else {
                dayCount = row.count();
            }
        }
        if (spec.cooldownSec() != null && spec.cooldownSec() > 0
                && lastCalled.plusSeconds(spec.cooldownSec()).isAfter(now)) {
            throw ToolFailure.of("RATE_LIMITED", "tool cooldown active");
        }
        if (spec.perSession() != null && sessionCount >= spec.perSession()) {
            throw ToolFailure.of("RATE_LIMITED", "per-session tool limit reached");
        }
        if (spec.perDay() != null && dayCount >= spec.perDay()) {
            throw ToolFailure.of("RATE_LIMITED", "per-day tool limit reached");
        }
        upsert(sessionId, toolCode, "session", now);
        if (spec.perDay() != null) {
            upsert(sessionId, toolCode, dayBucket, now);
        }
    }

    private void upsert(String sessionId, String toolCode, String bucket, Instant now) {
        jdbc.update(
                """
                INSERT INTO kjx_tool_rate_bucket (session_id, tool_code, bucket, count, last_called_at)
                VALUES (?, ?, ?, 1, ?)
                ON DUPLICATE KEY UPDATE count = count + 1, last_called_at = VALUES(last_called_at)
                """,
                sessionId,
                toolCode,
                bucket,
                Timestamp.from(now));
    }

    private static Instant toInstant(Object value) {
        if (value == null) {
            return Instant.EPOCH;
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toInstant();
        }
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime.atZone(ZONE).toInstant();
        }
        if (value instanceof Instant instant) {
            return instant;
        }
        throw new IllegalStateException("unsupported datetime type: " + value.getClass().getName());
    }

    private record BucketRow(String bucket, int count, Instant lastCalled) {
    }
}
