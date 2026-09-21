package com.koujunxin.ai.api.port;

import java.util.concurrent.atomic.AtomicBoolean;

public final class CancellationToken {

    private final AtomicBoolean cancelled = new AtomicBoolean();

    public void cancel() {
        cancelled.set(true);
    }

    public boolean isCancelled() {
        return cancelled.get();
    }

    public void throwIfCancelled() {
        if (cancelled.get()) {
            throw com.koujunxin.ai.api.AiException.cancelled();
        }
    }
}
