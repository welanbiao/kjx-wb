package com.koujunxin.work.visibility;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.koujunxin.work.api.WorkSnapshot;
import com.koujunxin.work.catalog.AccessForm;
import com.koujunxin.work.catalog.WorkStatus;
import com.koujunxin.work.catalog.WorkType;

class WorkVisibilityServiceTest {

    private final WorkVisibilityService first = new WorkVisibilityService();
    private final WorkVisibilityService second = new WorkVisibilityService();

    @Test
    void publishedAlwaysVisibleInCatalog() {
        WorkSnapshot work = snapshot(11L, WorkStatus.PUBLISHED, 0);
        assertTrue(first.visibleInPublicCatalog(work, 1L));
        assertTrue(first.canNewUnlock(work, 99L));
    }

    @Test
    void unpublishedNeverInPublicCatalogEvenIfEntitled() {
        WorkSnapshot work = snapshot(12L, WorkStatus.UNPUBLISHED, 100);
        assertFalse(first.visibleInPublicCatalog(work, 8L));
        assertFalse(first.canNewUnlock(work, 8L));
        assertTrue(first.canViewDetail(work, 8L, true));
        assertFalse(first.canViewDetail(work, 8L, false));
    }

    @Test
    void draftPendingRejectedHiddenFromApp() {
        for (WorkStatus status : new WorkStatus[] {WorkStatus.DRAFT, WorkStatus.PENDING_REVIEW, WorkStatus.REJECTED}) {
            WorkSnapshot work = snapshot(13L, status, 100);
            assertFalse(first.visibleInPublicCatalog(work, 1L));
            assertFalse(first.canViewDetail(work, 1L, true));
            assertFalse(first.canNewUnlock(work, 1L));
        }
    }

    @Test
    void grayZeroRatioInvisible() {
        WorkSnapshot work = snapshot(21L, WorkStatus.GRAY, 0);
        assertFalse(first.visibleInPublicCatalog(work, 1L));
    }

    @Test
    void grayFullRatioVisibleToAll() {
        WorkSnapshot work = snapshot(22L, WorkStatus.GRAY, 100);
        assertTrue(first.visibleInPublicCatalog(work, 1L));
        assertTrue(first.visibleInPublicCatalog(work, 9999L));
    }

    @Test
    void grayBucketStableForSameAccountAndWorkAcrossServiceInstances() {
        long accountId = 1001L;
        long workId = 77L;
        int a = first.grayBucket(accountId, workId);
        int b = second.grayBucket(accountId, workId);
        assertEquals(a, b);
        assertTrue(a >= 0 && a < 100);
        WorkSnapshot gray = snapshot(workId, WorkStatus.GRAY, 30);
        boolean visible = first.visibleInPublicCatalog(gray, accountId);
        for (int i = 0; i < 50; i++) {
            assertEquals(visible, second.visibleInPublicCatalog(gray, accountId));
        }
    }

    @Test
    void grayBucketDependsOnWorkIdNotOnlyAccount() {
        long accountId = 42L;
        Set<Integer> buckets = new HashSet<>();
        for (long workId = 1; workId <= 30; workId++) {
            buckets.add(first.grayBucket(accountId, workId));
        }
        assertTrue(buckets.size() > 1, "same account should not collapse all works into one bucket");
        assertNotEquals(first.grayBucket(accountId, 1L), first.grayBucket(99L, 1L));
    }

    @Test
    void grayRatioBoundaryIsDeterministic() {
        long workId = 55L;
        WorkSnapshot gray = snapshot(workId, WorkStatus.GRAY, 30);
        long inside = findAccount(workId, 30, true);
        long outside = findAccount(workId, 30, false);
        assertTrue(first.visibleInPublicCatalog(gray, inside));
        assertFalse(first.visibleInPublicCatalog(gray, outside));
        assertEquals(first.visibleInPublicCatalog(gray, inside), second.visibleInPublicCatalog(gray, inside));
        assertEquals(first.visibleInPublicCatalog(gray, outside), second.visibleInPublicCatalog(gray, outside));
    }

    private long findAccount(long workId, int ratio, boolean included) {
        for (long accountId = 1; accountId < 10_000; accountId++) {
            if (StableGrayBuckets.included(accountId, workId, ratio) == included) {
                return accountId;
            }
        }
        throw new IllegalStateException("unable to find account for gray fixture");
    }

    private static WorkSnapshot snapshot(long id, WorkStatus status, int grayRatio) {
        return new WorkSnapshot(
                id, "work-" + id, "n", WorkType.game, null, null, java.util.List.of(), null,
                java.util.List.of(), status, "1.0.0", "c1", AccessForm.A, java.util.List.of("PLATFORM"),
                grayRatio, 0, null, false);
    }
}
