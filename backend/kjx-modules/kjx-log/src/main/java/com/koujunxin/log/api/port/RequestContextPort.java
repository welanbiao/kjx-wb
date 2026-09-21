package com.koujunxin.log.api.port;

/**
 * 入口上下文：request_id / trace_id / span_id。由 Filter 绑定，业务经本端口读取。
 */
public interface RequestContextPort {

    RequestContextSnapshot current();

    void bind(RequestContextSnapshot snapshot);

    void clear();

    record RequestContextSnapshot(
            String requestId,
            String traceId,
            String spanId,
            String parentSpanId,
            String memberId,
            String loginType
    ) {
        public static RequestContextSnapshot of(String requestId, String traceId) {
            return new RequestContextSnapshot(requestId, traceId, null, null, null, null);
        }
    }
}
