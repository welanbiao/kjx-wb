package com.koujunxin.log.context;

import java.util.Locale;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.regex.Pattern;

import com.koujunxin.log.api.port.RequestContextPort;
import com.koujunxin.log.api.port.RequestContextPort.RequestContextSnapshot;
import com.koujunxin.log.api.port.TracePropagationPort;
import com.koujunxin.log.envelope.StructuredLogFields;

/**
 * 从 X-Request-Id / X-Trace-Id / W3C traceparent 提取并回写。
 */
public final class HeaderTracePropagation implements TracePropagationPort {

    private static final Pattern CORRELATION_ID = Pattern.compile("[A-Za-z0-9._:-]{1,64}");
    private static final Pattern TRACEPARENT = Pattern.compile(
            "^[0-9a-fA-F]{2}-([0-9a-fA-F]{32})-([0-9a-fA-F]{16})-[0-9a-fA-F]{2}$");

    private final RequestContextPort requestContextPort;

    public HeaderTracePropagation(RequestContextPort requestContextPort) {
        this.requestContextPort = requestContextPort;
    }

    @Override
    public String currentTraceId() {
        return requestContextPort.current().traceId();
    }

    @Override
    public String currentRequestId() {
        return requestContextPort.current().requestId();
    }

    @Override
    public String currentSpanId() {
        return requestContextPort.current().spanId();
    }

    @Override
    public void inject(BiConsumer<String, String> headerSetter) {
        RequestContextSnapshot snapshot = requestContextPort.current();
        if (snapshot.requestId() != null && !snapshot.requestId().isBlank()) {
            headerSetter.accept(StructuredLogFields.HEADER_REQUEST_ID, snapshot.requestId());
        }
        if (snapshot.traceId() != null && !snapshot.traceId().isBlank()) {
            headerSetter.accept(StructuredLogFields.HEADER_TRACE_ID, snapshot.traceId());
            String trace = w3cTraceId(snapshot.traceId());
            String span = w3cSpanId(snapshot.spanId());
            if (trace != null && span != null) {
                headerSetter.accept(StructuredLogFields.HEADER_TRACEPARENT,
                        "00-" + trace + "-" + span + "-01");
            }
        }
    }

    @Override
    public RequestContextSnapshot extract(Function<String, String> headerGetter) {
        String requestId = safeCorrelation(firstNonBlank(
                headerGetter.apply(StructuredLogFields.HEADER_REQUEST_ID),
                headerGetter.apply("x-request-id")));
        String traceId = safeCorrelation(firstNonBlank(
                headerGetter.apply(StructuredLogFields.HEADER_TRACE_ID),
                headerGetter.apply("x-trace-id")));
        String spanId = null;
        String parentSpanId = null;
        String traceparent = firstNonBlank(
                headerGetter.apply(StructuredLogFields.HEADER_TRACEPARENT),
                headerGetter.apply("Traceparent"));
        if (traceparent != null) {
            var match = TRACEPARENT.matcher(traceparent.trim());
            if (match.matches()) {
                if (traceId == null) {
                    traceId = match.group(1).toLowerCase(Locale.ROOT);
                }
                spanId = match.group(2).toLowerCase(Locale.ROOT);
            }
        }
        if (requestId == null) {
            requestId = MdcRequestContextPort.newRequestId();
        }
        if (traceId == null) {
            traceId = MdcRequestContextPort.newTraceId();
        }
        if (spanId == null) {
            spanId = MdcRequestContextPort.newTraceId().substring(0, 16);
        }
        return new RequestContextSnapshot(requestId, traceId, spanId, parentSpanId, null, null);
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private static String safeCorrelation(String value) {
        return value != null && CORRELATION_ID.matcher(value).matches() ? value : null;
    }

    private static String w3cTraceId(String value) {
        if (value == null) {
            return null;
        }
        String hex = value.toLowerCase(Locale.ROOT).replace("-", "");
        return hex.matches("[0-9a-f]{32}") && !hex.matches("0{32}") ? hex : null;
    }

    private static String w3cSpanId(String value) {
        String hex = value == null ? "" : value.toLowerCase(Locale.ROOT).replace("-", "");
        return hex.matches("[0-9a-f]{16}") && !hex.matches("0{16}") ? hex : null;
    }
}
