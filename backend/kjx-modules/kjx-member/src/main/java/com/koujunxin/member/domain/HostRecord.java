package com.koujunxin.member.domain;

import java.time.LocalDateTime;

public record HostRecord(
        long deptId,
        String hostCode,
        String dyAccount,
        String logoUrl,
        String contact,
        String remark,
        LocalDateTime createTime
) {
}
