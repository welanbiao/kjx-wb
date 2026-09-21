package com.koujunxin.ai.sse;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import com.koujunxin.ai.api.port.CancellationToken;
import com.koujunxin.ai.api.port.UpstreamChatClient;
import com.koujunxin.ai.api.port.UpstreamChatRequest;
import com.koujunxin.ai.api.port.UpstreamChatResponse;
import com.koujunxin.ai.api.port.UpstreamStreamListener;

/**
 * 把推式上游流拉成 token。取消后中断读取线程，不再继续消耗。
 */
public final class StreamingTokenSource implements TokenSource {

    private static final String END = "\0END";

    private final UpstreamChatClient client;
    private final UpstreamChatRequest request;
    private final BlockingQueue<String> queue = new LinkedBlockingQueue<>();
    private final AtomicInteger pulls = new AtomicInteger();
    private final AtomicBoolean ended = new AtomicBoolean();
    private volatile Thread worker;
    private volatile UpstreamChatResponse completed;
    private volatile boolean started;

    public StreamingTokenSource(UpstreamChatClient client, UpstreamChatRequest request) {
        this.client = client;
        this.request = request;
    }

    public UpstreamChatResponse completed() {
        return completed;
    }

    @Override
    public Optional<String> poll(CancellationToken cancel, Duration wait) {
        start(cancel);
        if (cancel.isCancelled()) {
            interruptWorker();
            ended.set(true);
            return Optional.empty();
        }
        try {
            String token = queue.poll(Math.max(1L, wait.toMillis()), TimeUnit.MILLISECONDS);
            if (END.equals(token)) {
                ended.set(true);
                return Optional.empty();
            }
            if (token != null) {
                pulls.incrementAndGet();
                return Optional.of(token);
            }
            return Optional.empty();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            interruptWorker();
            ended.set(true);
            return Optional.empty();
        }
    }

    @Override
    public boolean ended() {
        return ended.get();
    }

    @Override
    public int pulls() {
        return pulls.get();
    }

    private synchronized void start(CancellationToken cancel) {
        if (started) {
            return;
        }
        started = true;
        worker = Thread.ofVirtual().name("kjx-ai-upstream-stream").start(() -> {
            try {
                client.stream(request, new UpstreamStreamListener() {
                    @Override
                    public void onDelta(String textDelta) {
                        if (cancel.isCancelled() || textDelta == null) {
                            return;
                        }
                        queue.offer(textDelta);
                    }

                    @Override
                    public void onComplete(UpstreamChatResponse response) {
                        completed = response;
                        queue.offer(END);
                    }
                }, cancel);
                if (cancel.isCancelled()) {
                    ended.set(true);
                }
            } catch (RuntimeException ex) {
                ended.set(true);
                queue.offer(END);
                if (!cancel.isCancelled()) {
                    throw ex;
                }
            }
        });
    }

    private void interruptWorker() {
        Thread current = worker;
        if (current != null) {
            current.interrupt();
        }
    }
}
