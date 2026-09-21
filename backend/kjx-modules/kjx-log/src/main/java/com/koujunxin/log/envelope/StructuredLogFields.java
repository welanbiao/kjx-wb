package com.koujunxin.log.envelope;

/**
 * 统一结构化日志字段名（MDC / JSON 外壳）。对齐 docs/06 §3。
 */
public final class StructuredLogFields {

    public static final String LOG_ID = "log_id";
    public static final String TS = "ts";
    public static final String CATEGORY = "category";
    public static final String LEVEL = "level";
    public static final String TRACE_ID = "trace_id";
    public static final String SPAN_ID = "span_id";
    public static final String PARENT_SPAN_ID = "parent_span_id";
    public static final String REQUEST_ID = "request_id";
    public static final String IDEMPOTENCY_KEY = "idempotency_key";
    public static final String ACTOR = "actor";
    public static final String CONTEXT = "context";
    public static final String SOURCE = "source";
    public static final String OUTCOME = "outcome";
    public static final String METRICS = "metrics";
    public static final String PAYLOAD = "payload";
    public static final String SCHEMA_VERSION = "schema_version";
    public static final String MEMBER_ID = "member_id";
    public static final String LOGIN_TYPE = "login_type";

    public static final String HEADER_REQUEST_ID = "X-Request-Id";
    public static final String HEADER_TRACE_ID = "X-Trace-Id";
    public static final String HEADER_TRACEPARENT = "traceparent";

    private StructuredLogFields() {
    }
}
