package com.koujunxin.ai.observe;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.koujunxin.ai.api.port.LangfusePort;

/**
 * Langfuse 旁路。任何异常只记日志，不阻断主请求。禁止把 Prompt 或会话正文放进 trace。
 */
public final class SafeLangfuseReporter implements LangfusePort {

    private static final Logger log = LoggerFactory.getLogger(SafeLangfuseReporter.class);

    private final LangfusePort delegate;

    public SafeLangfuseReporter(LangfusePort delegate) {
        this.delegate = delegate;
    }

    @Override
    public void report(AiTrace trace) {
        if (delegate == null || trace == null) {
            return;
        }
        try {
            delegate.report(trace);
        } catch (RuntimeException ex) {
            log.warn("langfuse.report.failed turnId={} reason={}", trace.turnId(), ex.getClass().getSimpleName());
        }
    }
}
