package com.koujunxin.member.port;

import com.koujunxin.member.api.MemberFailures;
import com.koujunxin.member.api.port.MemberAuditPort;

public final class NoOpMemberAuditPort implements MemberAuditPort {

    @Override
    public void record(String targetType, long targetId, long deptId, String action, String reason, long actorId) {
        if (reason == null || reason.isBlank()) {
            throw MemberFailures.badRequest("reason 必填");
        }
    }
}
