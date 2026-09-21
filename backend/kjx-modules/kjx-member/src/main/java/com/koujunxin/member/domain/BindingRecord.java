package com.koujunxin.member.domain;

import java.time.LocalDateTime;

public record BindingRecord(
        long id,
        long deptId,
        long accountId,
        long memberId,
        String douyinIdInput,
        String douyinIdRaw,
        String memberCodeInput,
        int claimMonths,
        String status,
        LocalDateTime matchedAt,
        Long reviewerId,
        LocalDateTime reviewTime,
        String rejectReason,
        LocalDateTime unbindApplyAt,
        LocalDateTime unbindAt,
        LocalDateTime createTime
) {

    public static final String PENDING_REVIEW = "PENDING_REVIEW";
    public static final String MATCHED = "MATCHED";
    public static final String REJECTED = "REJECTED";
    public static final String UNBINDING = "UNBINDING";
    public static final String NONE = "NONE";
}
