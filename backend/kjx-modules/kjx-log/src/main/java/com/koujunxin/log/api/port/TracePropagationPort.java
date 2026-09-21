package com.koujunxin.log.api.port;

import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Trace 传播端口。默认基于请求头 / MDC，不强制引入 OTel SDK。
 * 集成方可在 kjx-server 用 OpenTelemetry 实现替换。
 */
public interface TracePropagationPort {

    String currentTraceId();

    String currentRequestId();

    String currentSpanId();

    void inject(BiConsumer<String, String> headerSetter);

    RequestContextPort.RequestContextSnapshot extract(Function<String, String> headerGetter);
}
