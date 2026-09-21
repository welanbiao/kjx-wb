package com.koujunxin.tool.execute;

import java.time.Instant;

import com.koujunxin.runtime.api.dto.ToolResult;

public interface ToolCallStore {

    CallClaim claim(
            long accountId,
            String workCode,
            String sessionId,
            String toolCode,
            String toolVersion,
            String idempotencyKey,
            String argumentsHash,
            Instant startedAt,
            int pendingTtlMs);

    void complete(long id, ToolResult result, Instant finishedAt, int durationMs);
}
