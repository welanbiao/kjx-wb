package com.koujunxin.tool.execute;

import java.time.Instant;

import com.koujunxin.tool.api.RateLimitSpec;

public interface RateLimitStore {

    void checkAndIncrement(String sessionId, String toolCode, RateLimitSpec spec, Instant now);
}
