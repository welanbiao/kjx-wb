package com.koujunxin.ai.sse;

import java.time.Duration;
import java.util.Optional;

import com.koujunxin.ai.api.port.CancellationToken;

/**
 * 可中断的拉式 token 源。取消后调用方不得再 poll。
 */
public interface TokenSource {

    /**
     * 最多等待 {@code wait}。空且 {@link #ended()} 为结束；空且未结束表示本轮无数据。
     */
    Optional<String> poll(CancellationToken cancel, Duration wait);

    boolean ended();

    int pulls();
}
