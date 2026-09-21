package com.koujunxin.server.integration;

import java.util.Map;

import com.koujunxin.log.api.port.AuditLogWritePort;
import com.koujunxin.log.api.port.RequestContextPort;
import com.koujunxin.member.api.port.MemberAuditPort;

final class MemberAuditAdapter implements MemberAuditPort {

    private final AuditLogWritePort audit;
    private final RequestContextPort contexts;

    MemberAuditAdapter(AuditLogWritePort audit, RequestContextPort contexts) {
        this.audit = audit;
        this.contexts = contexts;
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
    }
}
