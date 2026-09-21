package com.koujunxin.member.domain;

import java.time.LocalDateTime;

public record MemberRecord(
        long id,
        String memberCode,
        String douyinId,
        String douyinIdRaw,
        long deptId,
        int subscribedMonths,
        int subscribedDays,
        LocalDateTime expireAt,
        LocalDateTime startAt,
        int availableCount,
        int unlockedCount,
        int remainCount,
        String status,
        int bindState,
        Long boundAccountId,
        LocalDateTime boundAt,
        String remark,
        int version,
        LocalDateTime createTime
) {

    public static final int UNBOUND = 0;
    public static final int BOUND = 1;

    public boolean bound() {
        return bindState == BOUND;
    }
}
