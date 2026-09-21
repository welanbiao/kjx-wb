package com.koujunxin.ai.observe;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.koujunxin.ai.api.ModelTier;
import com.koujunxin.ai.api.RoutedModel;
import com.koujunxin.ai.api.TokenUsage;
import com.koujunxin.ai.api.port.LangfusePort;

class SafeLangfuseReporterTest {

    @Test
    void failuresDoNotPropagate() {
        AtomicInteger calls = new AtomicInteger();
        LangfusePort exploding = trace -> {
            calls.incrementAndGet();
            throw new IllegalStateException("langfuse down");
        };
        SafeLangfuseReporter reporter = new SafeLangfuseReporter(exploding);
        LangfusePort.AiTrace trace = new LangfusePort.AiTrace(
                "tr",
                "rq",
                "sess",
                "turn",
                "work",
                "1.0.0",
                RoutedModel.platform(ModelTier.STRONG, "kjx-strong"),
                TokenUsage.zero(),
                "ok",
                true);
        assertDoesNotThrow(() -> reporter.report(trace));
        assertEquals(1, calls.get());
        assertDoesNotThrow(() -> reporter.report(null));
    }
}
