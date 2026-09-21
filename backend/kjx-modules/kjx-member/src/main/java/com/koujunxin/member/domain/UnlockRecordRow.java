package com.koujunxin.member.domain;

import java.time.LocalDateTime;

/**
 * 解锁记录快照。不逻辑删除。
 */
public record UnlockRecordRow(
        long id,
        long deptId,
        long memberId,
        String memberCode,
        String douyinId,
        Long accountId,
        long workId,
        String workName,
        String workVersion,
        LocalDateTime unlockTime,
        int beforeUnlockedCount,
        int afterUnlockedCount,
        int beforeRemainCount,
        int afterRemainCount,
        String result,
        String failReason,
        String source,
        Long operatorId,
        String requestFingerprint,
        String idempotencyKey
) {

    public static final String SUCCESS = "SUCCESS";
    public static final String FAILED = "FAILED";
    public static final String USER = "user";
    public static final String OFFICIAL_ADD = "official_add";
    public static final String OFFICIAL_REMOVE = "official_remove";
    public static final String FIX = "fix";
}
