package com.koujunxin.log.api.port;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * MySQL AUDIT 写入。必须在当前业务事务内调用；失败应回滚业务。
 */
public interface AuditLogWritePort {

    void append(AuditLogCommand command);

    record AuditLogCommand(
            String logId,
            Instant ts,
            String traceId,
            String requestId,
            long adminId,
            String adminName,
            List<String> roles,
            String ip,
            String ipRegion,
            String action,
            String targetType,
            String targetId,
            Map<String, Object> before,
            Map<String, Object> after,
            String result,
            String reason
    ) {
    }
}
