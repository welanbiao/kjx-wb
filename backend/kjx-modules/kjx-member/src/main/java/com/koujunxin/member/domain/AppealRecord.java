package com.koujunxin.member.domain;

import java.time.LocalDateTime;
import java.util.List;

public record AppealRecord(
        long id,
        long accountId,
        Long bindingId,
        String appealType,
        String content,
        String contact,
        List<String> attachments,
        String status,
        Long handlerId,
        LocalDateTime handleTime,
        String handleNote,
        LocalDateTime createTime
) {

    public static final String PENDING = "PENDING";
    public static final String APPROVED = "APPROVED";
    public static final String REJECTED = "REJECTED";

    public static final String CHANGE_DOUYIN = "CHANGE_DOUYIN";
    public static final String WRONG_BIND = "WRONG_BIND";
    public static final String OTHER = "OTHER";
}
