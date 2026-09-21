package com.koujunxin.member.api.port;

/**
 * 关键修改 AUDIT。默认空实现；kjx-server / WP-50 接入 log_audit。
 */
public interface MemberAuditPort {

    void record(String targetType, long targetId, long deptId, String action, String reason, long actorId);
}
