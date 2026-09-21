package com.koujunxin.work.query;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.koujunxin.work.api.WorkDetailView;
import com.koujunxin.work.api.WorkPageView;
import com.koujunxin.work.catalog.AccessForm;
import com.koujunxin.work.catalog.InMemoryWorkRepository;
import com.koujunxin.work.catalog.WorkNotFoundException;
import com.koujunxin.work.catalog.WorkRecord;
import com.koujunxin.work.catalog.WorkStatus;
import com.koujunxin.work.catalog.WorkType;
import com.koujunxin.work.catalog.WorkUnpublishedException;
import com.koujunxin.work.support.InMemoryEntitlementQueryPort;
import com.koujunxin.work.visibility.WorkVisibilityService;

class PublicWorkQueryServiceTest {

    private InMemoryWorkRepository works;
    private InMemoryEntitlementQueryPort entitlements;
    private PublicWorkQueryService query;
    private WorkVisibilityService visibility;

    @BeforeEach
    void setUp() {
        works = new InMemoryWorkRepository();
        entitlements = new InMemoryEntitlementQueryPort();
        visibility = new WorkVisibilityService();
        query = new PublicWorkQueryService(works, visibility, entitlements);
    }

    @Test
    void publicListContainsPublishedAndVisibleGrayOnly() {
        WorkRecord published = insert("pub-a", WorkStatus.PUBLISHED, 0);
        WorkRecord unpublished = insert("down-a", WorkStatus.UNPUBLISHED, 0);
        WorkRecord draft = insert("draft-a", WorkStatus.DRAFT, 0);
        long accountId = 9L;
        WorkRecord gray = insert("gray-a", WorkStatus.GRAY, 30);
        WorkPageView page = query.list(accountId, null, null, 1, 20);
        assertTrue(page.list().stream().anyMatch(item -> item.workCode().equals(published.workCode())));
        assertFalse(page.list().stream().anyMatch(item -> item.workCode().equals(unpublished.workCode())));
        assertFalse(page.list().stream().anyMatch(item -> item.workCode().equals(draft.workCode())));
        boolean grayVisible = visibility.visibleInPublicCatalog(gray, accountId);
        assertEquals(grayVisible, page.list().stream().anyMatch(item -> item.workCode().equals("gray-a")));
    }

    @Test
    void unpublishedNotInDefaultListEvenWhenEntitled() {
        WorkRecord unpublished = insert("down-b", WorkStatus.UNPUBLISHED, 0);
        entitlements.grant(3L, unpublished.id());
        WorkPageView page = query.list(3L, null, null, 1, 20);
        assertFalse(page.list().stream().anyMatch(item -> item.workCode().equals("down-b")));
    }

    @Test
    void unlockedTrueIncludesEntitledUnpublished() {
        WorkRecord unpublished = insert("down-c", WorkStatus.UNPUBLISHED, 0);
        entitlements.grant(4L, unpublished.id());
        WorkPageView page = query.list(4L, null, true, 1, 20);
        assertEquals(1, page.total());
        assertEquals("down-c", page.list().get(0).workCode());
        assertTrue(page.list().get(0).unlocked());
        WorkPageView otherUser = query.list(5L, null, true, 1, 20);
        assertEquals(0, otherUser.total());
    }

    @Test
    void detailAllowsEntitledUnpublishedAndRejectsOthers() {
        WorkRecord unpublished = insert("down-d", WorkStatus.UNPUBLISHED, 0);
        entitlements.grant(6L, unpublished.id());
        WorkDetailView detail = query.getDetail(6L, "down-d");
        assertEquals("down-d", detail.workCode());
        assertTrue(detail.unlocked());
        assertEquals("/runtime/down-d", detail.entryUrl());
        assertTrue(query.canAccessUnpublishedIfEntitled(6L, "down-d"));
        assertThrows(WorkUnpublishedException.class, () -> query.getDetail(7L, "down-d"));
        assertFalse(query.canAccessUnpublishedIfEntitled(7L, "down-d"));
    }

    @Test
    void missingWorkIsNotFound() {
        assertThrows(WorkNotFoundException.class, () -> query.getDetail(1L, "no-such"));
    }

    @Test
    void grayInvisibleAccountGetsNotFoundOnDetail() {
        WorkRecord gray = insert("gray-hide", WorkStatus.GRAY, 10);
        long outside = 1L;
        while (visibility.visibleInPublicCatalog(gray, outside)) {
            outside++;
        }
        long inside = 1L;
        while (!visibility.visibleInPublicCatalog(gray, inside)) {
            inside++;
        }
        long outsideAccount = outside;
        long insideAccount = inside;
        assertThrows(WorkNotFoundException.class, () -> query.getDetail(outsideAccount, "gray-hide"));
        WorkDetailView detail = query.getDetail(insideAccount, "gray-hide");
        assertEquals("gray-hide", detail.workCode());
        assertFalse(detail.unlocked());
        assertEquals(null, detail.entryUrl());
    }

    private WorkRecord insert(String code, WorkStatus status, int grayRatio) {
        Instant now = Instant.parse("2026-09-21T12:00:00Z");
        return works.insert(new WorkRecord(
                null, code, code, WorkType.game, "s", null, List.of(), "cat", List.of("tag"),
                status, "1.0.0", "c1", AccessForm.A, "SERVER", List.of("PLATFORM"), false,
                List.of(), List.of(), List.of(), null, grayRatio, 10, 0, now, "op", now, "op", now, false));
    }
}
