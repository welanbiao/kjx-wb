package com.koujunxin.log.context;

import java.util.UUID;

import org.slf4j.MDC;

import com.koujunxin.log.api.port.RequestContextPort;
import com.koujunxin.log.envelope.StructuredLogFields;

/**
 * ThreadLocal + MDC。Filter 必须在 finally 中 {@link #clear()}，避免请求串上下文。
 */
public final class MdcRequestContextPort implements RequestContextPort {

    private static final ThreadLocal<RequestContextSnapshot> HOLDER = new ThreadLocal<>();

    @Override
    public RequestContextSnapshot current() {
        RequestContextSnapshot snapshot = HOLDER.get();
        if (snapshot != null) {
            return snapshot;
        }
        return RequestContextSnapshot.of(
                MDC.get(StructuredLogFields.REQUEST_ID),
                MDC.get(StructuredLogFields.TRACE_ID));
    }

    @Override
    public void bind(RequestContextSnapshot snapshot) {
        HOLDER.set(snapshot);
        put(StructuredLogFields.REQUEST_ID, snapshot.requestId());
        put(StructuredLogFields.TRACE_ID, snapshot.traceId());
        put(StructuredLogFields.SPAN_ID, snapshot.spanId());
        put(StructuredLogFields.PARENT_SPAN_ID, snapshot.parentSpanId());
        put(StructuredLogFields.MEMBER_ID, snapshot.memberId());
        put(StructuredLogFields.LOGIN_TYPE, snapshot.loginType());
    }

    @Override
    public void clear() {
        HOLDER.remove();
        MDC.remove(StructuredLogFields.REQUEST_ID);
        MDC.remove(StructuredLogFields.TRACE_ID);
        MDC.remove(StructuredLogFields.SPAN_ID);
        MDC.remove(StructuredLogFields.PARENT_SPAN_ID);
        MDC.remove(StructuredLogFields.MEMBER_ID);
        MDC.remove(StructuredLogFields.LOGIN_TYPE);
    }

    public static String newRequestId() {
        return UUID.randomUUID().toString();
    }

    public static String newTraceId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private static void put(String key, String value) {
        if (value == null || value.isBlank()) {
            MDC.remove(key);
        } else {
            MDC.put(key, value);
        }
    }
}
