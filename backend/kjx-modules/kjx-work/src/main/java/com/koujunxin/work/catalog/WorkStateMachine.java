package com.koujunxin.work.catalog;

import java.util.Map;
import java.util.Set;

/**
 * 作品目录状态机。审核通过后才可上架/灰度；下架后可重新上架或灰度。
 */
public final class WorkStateMachine {

    private static final Map<WorkStatus, Set<WorkStatus>> ALLOWED = Map.of(
            WorkStatus.DRAFT, Set.of(WorkStatus.PENDING_REVIEW),
            WorkStatus.PENDING_REVIEW, Set.of(
                    WorkStatus.PUBLISHED, WorkStatus.GRAY, WorkStatus.REJECTED, WorkStatus.DRAFT),
            WorkStatus.REJECTED, Set.of(WorkStatus.DRAFT, WorkStatus.PENDING_REVIEW),
            WorkStatus.PUBLISHED, Set.of(WorkStatus.GRAY, WorkStatus.UNPUBLISHED),
            WorkStatus.GRAY, Set.of(WorkStatus.PUBLISHED, WorkStatus.UNPUBLISHED),
            WorkStatus.UNPUBLISHED, Set.of(WorkStatus.PUBLISHED, WorkStatus.GRAY)
    );

    private WorkStateMachine() {
    }

    public static boolean canTransit(WorkStatus from, WorkStatus to) {
        if (from == null || to == null) {
            return false;
        }
        if (from == to) {
            return true;
        }
        return ALLOWED.getOrDefault(from, Set.of()).contains(to);
    }

    public static void requireTransit(WorkStatus from, WorkStatus to) {
        if (!canTransit(from, to)) {
            throw new InvalidWorkTransitionException(from, to);
        }
    }

    public static boolean isLiveStatus(WorkStatus status) {
        return status == WorkStatus.PUBLISHED || status == WorkStatus.GRAY;
    }

    public static boolean shouldStampPublishTime(WorkStatus from, WorkStatus to) {
        return isLiveStatus(to) && !isLiveStatus(from);
    }

    public static boolean isPublishEvent(WorkStatus from, WorkStatus to) {
        return from != to && isLiveStatus(to);
    }

    public static boolean isUnpublishEvent(WorkStatus from, WorkStatus to) {
        return from != to && to == WorkStatus.UNPUBLISHED;
    }
}
