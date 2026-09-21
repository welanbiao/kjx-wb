package com.koujunxin.member.support;

import java.util.ArrayList;
import java.util.List;

import com.koujunxin.member.api.MemberFailures;
import com.koujunxin.member.api.port.MemberAuditPort;

public final class RecordingMemberAuditPort implements MemberAuditPort {

    public record Entry(String targetType, long targetId, long deptId, String action, String reason, long actorId) {
    }

    private final List<Entry> entries = new ArrayList<>();

    @Override
    public void record(String targetType, long targetId, long deptId, String action, String reason, long actorId) {
        if (reason == null || reason.isBlank()) {
            throw MemberFailures.badRequest("reason 必填");
        }
        entries.add(new Entry(targetType, targetId, deptId, action, reason, actorId));
    }

    public List<Entry> entries() {
        return List.copyOf(entries);
    }

    public void clear() {
        entries.clear();
    }
}
