package com.koujunxin.server.integration;

import java.util.Map;

import com.koujunxin.log.api.port.AuditLogWritePort;
import com.koujunxin.log.api.port.RequestContextPort;
import com.koujunxin.work.port.WorkAuditPort;

final class WorkAuditAdapter implements WorkAuditPort {

    private final AuditLogWritePort audit;
    private final RequestContextPort contexts;

    WorkAuditAdapter(AuditLogWritePort audit, RequestContextPort contexts) {
        this.audit = audit;
        this.contexts = contexts;
    }

    @Override
    public void record(long workId, String action, String reason, String actor) {
        audit.append(AuditCommands.success(
                contexts,
                AuditCommands.currentActorId(actor),
                AuditCommands.currentActorName(actor),
                action,
                "work",
                String.valueOf(workId),
                Map.of(),
                Map.of(),
                reason));
    }
}
