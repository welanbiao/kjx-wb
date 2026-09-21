package com.koujunxin.work.visibility;

import com.koujunxin.work.api.WorkSnapshot;
import com.koujunxin.work.catalog.WorkRecord;
import com.koujunxin.work.catalog.WorkStatus;

/**
 * 作品可见性的唯一实现。列表、详情、新解锁必须调用本服务，禁止旁路复制灰度公式。
 */
public final class WorkVisibilityService {

    public boolean visibleInPublicCatalog(WorkRecord work, long accountId) {
        return visibleInPublicCatalog(WorkSnapshot.from(work), accountId);
    }

    public boolean visibleInPublicCatalog(WorkSnapshot work, long accountId) {
        if (work == null || work.deleted()) {
            return false;
        }
        return switch (work.status()) {
            case PUBLISHED -> true;
            case GRAY -> StableGrayBuckets.included(accountId, work.id(), work.grayRatio());
            default -> false;
        };
    }

    /**
     * 详情：公开可见，或已有 entitlement 的下架作品。草稿/待审/驳回对 C 端不可见。
     */
    public boolean canViewDetail(WorkSnapshot work, long accountId, boolean entitled) {
        if (visibleInPublicCatalog(work, accountId)) {
            return true;
        }
        return entitled && work != null && !work.deleted() && work.status() == WorkStatus.UNPUBLISHED;
    }

    public boolean canViewDetail(WorkRecord work, long accountId, boolean entitled) {
        return canViewDetail(WorkSnapshot.from(work), accountId, entitled);
    }

    /**
     * 新解锁：必须公开可见。已有 entitlement 由 WP-13 在本方法之前短路为 1010。
     */
    public boolean canNewUnlock(WorkSnapshot work, long accountId) {
        return visibleInPublicCatalog(work, accountId);
    }

    public boolean canNewUnlock(WorkRecord work, long accountId) {
        return canNewUnlock(WorkSnapshot.from(work), accountId);
    }

    public int grayBucket(long accountId, long workId) {
        return StableGrayBuckets.bucket(accountId, workId);
    }
}
