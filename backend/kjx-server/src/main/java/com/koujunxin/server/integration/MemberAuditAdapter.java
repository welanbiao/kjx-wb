package com.koujunxin.server.integration;

import java.time.Instant;
import java.util.Map;

import org.springframework.beans.factory.ObjectProvider;

import com.koujunxin.log.api.port.AuditLogWritePort;
import com.koujunxin.log.api.port.RequestContextPort;
import com.koujunxin.log.api.port.SecLogWritePort;
import com.koujunxin.log.api.port.SecLogWritePort.SecLogCommand;
import com.koujunxin.member.api.port.MemberAuditPort;

final class MemberAuditAdapter implements MemberAuditPort {

    static final String SEC_MISMATCH = "SEC_MISMATCH";

    private final AuditLogWritePort audit;
    private final RequestContextPort contexts;
    private final ObjectProvider<SecLogWritePort> securityLogs;

    MemberAuditAdapter(
            AuditLogWritePort audit,
            RequestContextPort contexts,
            ObjectProvider<SecLogWritePort> securityLogs) {
        this.audit = audit;
        this.contexts = contexts;
        this.securityLogs = securityLogs;
    }

    @Override
    public void record(
            String targetType,
            long targetId,
            long deptId,
            String action,
            String reason,
            long actorId) {
        audit.append(AuditCommands.success(
                contexts,
                actorId,
                AuditCommands.currentActorName(String.valueOf(actorId)),
                action,
                targetType,
                String.valueOf(targetId),
                Map.of(),
                Map.of("deptId", deptId),
                reason));
        if (SEC_MISMATCH.equals(action)) {
            appendSecurityMismatch(targetType, targetId, deptId, actorId, reason);
        }
    }

    private void appendSecurityMismatch(
            String targetType, long targetId, long deptId, long actorId, String reason) {
        SecLogWritePort security = securityLogs.getIfAvailable();
        if (security == null) {
            return;
        }
        RequestContextPort.RequestContextSnapshot context = contexts.current();
        security.append(new SecLogCommand(
                null,
                Instant.now(),
                AuditCommands.valueOrUuid(context == null ? null : context.traceId()),
                AuditCommands.valueOrUuid(context == null ? null : context.requestId()),
                SEC_MISMATCH,
                null,
                AuditCommands.clientIp(),
                "DENY",
                reason,
                Map.of(
                        "targetType", targetType,
                        "targetId", targetId,
                        "deptId", deptId,
                        "actorId", actorId)));
    }
}
