package com.koujunxin.account.domain;

import java.time.LocalDateTime;
import java.util.Objects;

public record AccountRecord(
        long id,
        String phoneCipher,
        String phoneHash,
        String passwordHash,
        String nickname,
        String avatar,
        int status,
        int bindStatus,
        LocalDateTime cancelApplyAt,
        LocalDateTime lastLoginAt,
        LocalDateTime createTime
) {

    public static final int STATUS_NORMAL = 1;
    public static final int STATUS_LOCKED = 2;
    public static final int STATUS_CANCELLING = 3;
    public static final int STATUS_CANCELLED = 4;

    public AccountRecord {
        Objects.requireNonNull(phoneHash, "phoneHash");
        Objects.requireNonNull(passwordHash, "passwordHash");
    }

    public boolean locked() {
        return status == STATUS_LOCKED;
    }

    public boolean cancelling() {
        return status == STATUS_CANCELLING;
    }

    public boolean cancelled() {
        return status == STATUS_CANCELLED;
    }
}
