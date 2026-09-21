package com.koujunxin.ai.sse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.koujunxin.ai.api.AiException;
import com.koujunxin.ai.api.port.CancellationToken;
import com.koujunxin.ai.openai.OpenAiCodec;

public class SsePumpTest {

    private final OpenAiCodec codec = new OpenAiCodec();

    @Test
    void cancelStopsPullingRemainingTokens() throws Exception {
        DelayedTokenSource source = new DelayedTokenSource(20, 40);
        CollectingSink sink = new CollectingSink();
        CancellationToken cancel = new CancellationToken();
        SsePump pump = new SsePump(codec, new SseLimits(Duration.ofSeconds(5), Duration.ofSeconds(5), Duration.ofSeconds(5)), Clock.systemUTC());
        Thread worker = Thread.ofVirtual().start(() -> {
            try {
                pump.run("chatcmpl-x", "kjx-strong", source, sink, cancel);
            } catch (AiException ignored) {
                // cancelled
            }
        });
        Thread.sleep(110);
        cancel.cancel();
        worker.join(Duration.ofSeconds(2));
        assertTrue(source.pulls() <= 5, "pulls=" + source.pulls());
        assertTrue(source.pulls() >= 1);
        assertTrue(source.pulls() < 20);
        assertTrue(sink.completed);
    }

    @Test
    void heartbeatCommentsAreEmittedWhileWaiting() {
        DelayedTokenSource source = new DelayedTokenSource(3, 80);
        CollectingSink sink = new CollectingSink();
        SsePump pump = new SsePump(
                codec,
                new SseLimits(Duration.ofMillis(30), Duration.ofSeconds(2), Duration.ofSeconds(5)),
                Clock.systemUTC());
        String text = pump.run("chatcmpl-h", "kjx-strong", source, sink, new CancellationToken());
        assertEquals("012", text);
        assertTrue(sink.comments.stream().anyMatch(comment -> comment.contains("ping")));
        assertTrue(sink.data.stream().anyMatch(item -> item.contains("[DONE]")));
    }

    @Test
    void idleTimeoutCancelsInsteadOfWaitingForever() {
        TokenSource hung = new TokenSource() {
            private boolean ended;

            @Override
            public Optional<String> poll(CancellationToken cancel, Duration wait) {
                try {
                    Thread.sleep(Math.min(40, wait.toMillis()));
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
                return Optional.empty();
            }

            @Override
            public boolean ended() {
                return ended;
            }

            @Override
            public int pulls() {
                return 0;
            }
        };
        SsePump pump = new SsePump(
                codec,
                new SseLimits(Duration.ofSeconds(5), Duration.ofMillis(80), Duration.ofSeconds(2)),
                Clock.systemUTC());
        AiException error = assertThrows(AiException.class,
                () -> pump.run("chatcmpl-t", "kjx-strong", hung, new CollectingSink(), new CancellationToken()));
        assertEquals("AI_UPSTREAM_TIMEOUT", error.name());
    }

    private static final class DelayedTokenSource implements TokenSource {
        private final int count;
        private final long delayMillis;
        private final AtomicInteger index = new AtomicInteger();
        private volatile boolean ended;
        private long lastEmit = System.nanoTime();

        private DelayedTokenSource(int count, long delayMillis) {
            this.count = count;
            this.delayMillis = delayMillis;
        }

        @Override
        public Optional<String> poll(CancellationToken cancel, Duration wait) {
            if (cancel.isCancelled() || ended) {
                return Optional.empty();
            }
            if (index.get() >= count) {
                ended = true;
                return Optional.empty();
            }
            long due = lastEmit + delayMillis * 1_000_000L;
            long remainingMs = Math.max(0, (due - System.nanoTime()) / 1_000_000L);
            long slice = Math.min(remainingMs, Math.max(1L, wait.toMillis()));
            if (remainingMs > wait.toMillis()) {
                try {
                    Thread.sleep(slice);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
                return Optional.empty();
            }
            try {
                if (slice > 0) {
                    Thread.sleep(slice);
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return Optional.empty();
            }
            if (cancel.isCancelled()) {
                return Optional.empty();
            }
            lastEmit = System.nanoTime();
            int current = index.getAndIncrement();
            if (current >= count) {
                ended = true;
                return Optional.empty();
            }
            return Optional.of(String.valueOf(current));
        }

        @Override
        public boolean ended() {
            return ended;
        }

        @Override
        public int pulls() {
            return index.get();
        }
    }

    public static final class CollectingSink implements SseSink {
        final List<String> comments = new CopyOnWriteArrayList<>();
        final List<String> data = new CopyOnWriteArrayList<>();
        volatile boolean completed;

        @Override
        public void comment(String comment) {
            comments.add(comment);
        }

        @Override
        public void data(String payload) {
            data.add(payload);
        }

        @Override
        public void complete() {
            completed = true;
        }
    }
}
