package com.koujunxin.log.api.port;

import java.time.Instant;
import java.util.Map;

/**
 * MySQL BIZ 写入。与业务同事务调用。
 */
public interface BizLogWritePort {

    void append(BizLogCommand command);

    record BizLogCommand(
            String logId,
            Instant ts,
            String traceId,
            String requestId,
            String actorType,
            Long memberId,
            String workId,
            String action,
            String outcome,
            String reason,
            Map<String, Object> payload
    ) {
    }
}
