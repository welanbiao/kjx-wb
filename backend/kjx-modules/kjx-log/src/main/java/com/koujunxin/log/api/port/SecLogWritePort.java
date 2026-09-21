package com.koujunxin.log.api.port;

import java.time.Instant;
import java.util.Map;

/**
 * MySQL SEC 写入。有事务则加入，无事务也允许（如登录失败）。
 */
public interface SecLogWritePort {

    void append(SecLogCommand command);

    record SecLogCommand(
            String logId,
            Instant ts,
            String traceId,
            String requestId,
            String eventType,
            Long memberId,
            String ip,
            String outcome,
            String reason,
            Map<String, Object> payload
    ) {
    }
}
