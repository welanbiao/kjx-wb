package com.koujunxin.work.catalog;

/**
 * 作品状态。公开列表仅 PUBLISHED/GRAY（GRAY 再经 WorkVisibilityService）。
 */
public enum WorkStatus {
    DRAFT,
    PENDING_REVIEW,
    PUBLISHED,
    GRAY,
    UNPUBLISHED,
    REJECTED;

    public static WorkStatus from(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("work status is required");
        }
        return WorkStatus.valueOf(raw.trim());
    }
}
