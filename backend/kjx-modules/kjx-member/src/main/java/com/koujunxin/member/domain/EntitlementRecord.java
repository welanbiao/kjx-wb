package com.koujunxin.member.domain;

import java.time.LocalDateTime;

/**
 * 权益成功事实。访问只认本行，不得从 unlocked_count 推断。
 */
public record EntitlementRecord(
        long id,
        long deptId,
        long memberId,
        Long accountId,
        long workId,
        String workVersion,
        String source,
        Long unlockRecordId,
        LocalDateTime unlockedAt
) {

    public static final String USER = "user";
    public static final String OFFICIAL_ADD = "official_add";
}
