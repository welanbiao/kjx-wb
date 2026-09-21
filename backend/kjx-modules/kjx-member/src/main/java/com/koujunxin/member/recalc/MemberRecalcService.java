package com.koujunxin.member.recalc;

import java.time.LocalDateTime;

/**
 * 唯一额度/状态重算。available_count = floor(subscribed_days / 30)。
 * 禁止使用 subscribed_months 或用户 claimMonths。
 */
public final class MemberRecalcService {

    public RecalcResult recalc(int subscribedDays, int unlockedCount, LocalDateTime startAt, LocalDateTime expireAt,
                               LocalDateTime now) {
        if (subscribedDays < 0) {
            throw new IllegalArgumentException("subscribedDays must be >= 0");
        }
        if (unlockedCount < 0) {
            throw new IllegalArgumentException("unlockedCount must be >= 0");
        }
        int availableCount = subscribedDays / 30;
        int remainCount = Math.max(0, availableCount - unlockedCount);
        return new RecalcResult(availableCount, unlockedCount, remainCount, status(startAt, expireAt, now));
    }

    public String status(LocalDateTime startAt, LocalDateTime expireAt, LocalDateTime now) {
        if (expireAt == null || now == null) {
            throw new IllegalArgumentException("expireAt and now are required");
        }
        if (startAt != null && now.isBefore(startAt)) {
            return "NOT_STARTED";
        }
        if (now.isAfter(expireAt)) {
            return "EXPIRED";
        }
        return "VALID";
    }

    public record RecalcResult(int availableCount, int unlockedCount, int remainCount, String status) {
    }
}
