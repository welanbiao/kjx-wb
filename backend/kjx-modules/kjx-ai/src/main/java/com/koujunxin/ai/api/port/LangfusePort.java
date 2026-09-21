package com.koujunxin.ai.api.port;

import com.koujunxin.ai.api.RoutedModel;
import com.koujunxin.ai.api.TokenUsage;

public interface LangfusePort {

    void report(AiTrace trace);

    record AiTrace(
            String traceId,
            String requestId,
            String sessionId,
            String turnId,
            String workCode,
            String promptVersion,
            RoutedModel route,
            TokenUsage usage,
            String outcome,
            boolean langfuseShouldIgnoreContent
    ) {
        @Override
        public String toString() {
            return "AiTrace[sessionId=" + sessionId + ", turnId=" + turnId + ", outcome=" + outcome + "]";
        }
    }
}
