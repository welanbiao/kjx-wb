package com.koujunxin.ai.support;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

import com.koujunxin.ai.api.TokenUsage;
import com.koujunxin.ai.api.port.CancellationToken;
import com.koujunxin.ai.api.port.UpstreamChatClient;
import com.koujunxin.ai.api.port.UpstreamChatRequest;
import com.koujunxin.ai.api.port.UpstreamChatResponse;
import com.koujunxin.ai.api.port.UpstreamStreamListener;
import com.koujunxin.ai.openai.OpenAiMessage;

/**
 * 假上游。禁止访问网络。
 */
public final class ScriptedUpstreamClient implements UpstreamChatClient {

    private final List<Function<UpstreamChatRequest, UpstreamChatResponse>> scripts = new ArrayList<>();
    private final List<String> streamChunks = new CopyOnWriteArrayList<>();
    private volatile long chunkDelayMillis;
    private volatile int completeCalls;
    private volatile int streamChunksEmitted;
    private volatile UpstreamChatRequest lastRequest;

    public ScriptedUpstreamClient enqueue(Function<UpstreamChatRequest, UpstreamChatResponse> script) {
        scripts.add(script);
        return this;
    }

    public ScriptedUpstreamClient chunks(List<String> chunks) {
        streamChunks.clear();
        streamChunks.addAll(chunks);
        return this;
    }

    public ScriptedUpstreamClient chunkDelayMillis(long delay) {
        this.chunkDelayMillis = delay;
        return this;
    }

    public int completeCalls() {
        return completeCalls;
    }

    public int streamChunksEmitted() {
        return streamChunksEmitted;
    }

    public UpstreamChatRequest lastRequest() {
        return lastRequest;
    }

    @Override
    public UpstreamChatResponse complete(UpstreamChatRequest request, CancellationToken cancel) {
        lastRequest = request;
        cancel.throwIfCancelled();
        int index = completeCalls++;
        Function<UpstreamChatRequest, UpstreamChatResponse> script = index < scripts.size()
                ? scripts.get(index)
                : req -> text("你好");
        return script.apply(request);
    }

    @Override
    public void stream(UpstreamChatRequest request, UpstreamStreamListener listener, CancellationToken cancel) {
        lastRequest = request;
        List<String> chunks = streamChunks.isEmpty() ? List.of("你", "好") : List.copyOf(streamChunks);
        StringBuilder acc = new StringBuilder();
        for (String chunk : chunks) {
            if (cancel.isCancelled()) {
                return;
            }
            if (chunkDelayMillis > 0) {
                try {
                    Thread.sleep(chunkDelayMillis);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
            if (cancel.isCancelled()) {
                return;
            }
            streamChunksEmitted++;
            acc.append(chunk);
            listener.onDelta(chunk);
        }
        if (!cancel.isCancelled()) {
            listener.onComplete(text(acc.toString()));
        }
    }

    public static UpstreamChatResponse text(String content) {
        return new UpstreamChatResponse(
                "chatcmpl-fake",
                "fake",
                OpenAiMessage.assistant(content),
                "stop",
                new TokenUsage(8, Math.max(1, content.length() / 4), 0));
    }
}
