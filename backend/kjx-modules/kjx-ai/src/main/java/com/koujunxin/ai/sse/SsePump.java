package com.koujunxin.ai.sse;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import com.koujunxin.ai.api.AiException;
import com.koujunxin.ai.api.port.CancellationToken;
import com.koujunxin.ai.openai.OpenAiChunk;
import com.koujunxin.ai.openai.OpenAiCodec;

/**
 * SSE 心跳、空闲超时、总超时。客户端取消后停止拉取上游。
 */
public final class SsePump {

    private static final Duration POLL = Duration.ofMillis(50);

    private final OpenAiCodec codec;
    private final SseLimits limits;
    private final Clock clock;

    public SsePump(OpenAiCodec codec, SseLimits limits, Clock clock) {
        this.codec = codec;
        this.limits = limits;
        this.clock = clock;
    }

    public String run(
            String completionId,
            String model,
            TokenSource source,
            SseSink sink,
            CancellationToken cancel
    ) {
        Instant started = clock.instant();
        Instant lastData = started;
        Instant lastBeat = started;
        StringBuilder acc = new StringBuilder();
        long created = started.getEpochSecond();
        try {
            while (!cancel.isCancelled()) {
                Instant now = clock.instant();
                if (Duration.between(started, now).compareTo(limits.totalTimeout()) > 0) {
                    cancel.cancel();
                    throw AiException.timeout();
                }
                if (Duration.between(lastData, now).compareTo(limits.idleTimeout()) > 0) {
                    cancel.cancel();
                    throw AiException.timeout();
                }
                if (Duration.between(lastBeat, now).compareTo(limits.heartbeat()) >= 0) {
                    sink.comment("ping");
                    lastBeat = now;
                }
                Optional<String> token = source.poll(cancel, POLL);
                if (cancel.isCancelled()) {
                    break;
                }
                if (source.ended() && token.isEmpty()) {
                    sink.data(codec.writeChunk(OpenAiChunk.stop(completionId, model, created)));
                    sink.data("[DONE]");
                    sink.complete();
                    return acc.toString();
                }
                if (token.isPresent()) {
                    acc.append(token.get());
                    lastData = clock.instant();
                    sink.data(codec.writeChunk(OpenAiChunk.delta(completionId, model, created, token.get())));
                }
            }
            throw AiException.cancelled();
        } catch (AiException ex) {
            if ("AI_CANCELLED".equals(ex.name()) || "AI_UPSTREAM_TIMEOUT".equals(ex.name())) {
                try {
                    sink.complete();
                } catch (RuntimeException ignored) {
                    // client already gone
                }
            }
            throw ex;
        }
    }
}
