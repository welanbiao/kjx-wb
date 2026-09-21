package com.koujunxin.work.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.koujunxin.work.api.WorkDetailView;
import com.koujunxin.work.api.WorkPageView;
import com.koujunxin.work.catalog.AccessForm;
import com.koujunxin.work.catalog.CreateWorkCommand;
import com.koujunxin.work.catalog.UpdateWorkCommand;
import com.koujunxin.work.catalog.WorkCatalogService;
import com.koujunxin.work.catalog.WorkRecord;
import com.koujunxin.work.catalog.WorkStatus;
import com.koujunxin.work.catalog.WorkType;
import com.koujunxin.work.catalog.WorkUnpublishedException;
import com.koujunxin.work.query.PublicWorkQueryService;
import com.koujunxin.work.support.InMemoryEntitlementQueryPort;
import com.koujunxin.work.visibility.WorkVisibilityService;

class PublicWorkQueryMysqlTest extends MysqlWorkSupport {

    private WorkCatalogService catalog;
    private InMemoryEntitlementQueryPort entitlements;
    private PublicWorkQueryService query;

    @BeforeEach
    void setUp() {
        catalog = catalog();
        entitlements = new InMemoryEntitlementQueryPort();
        query = publicQuery(entitlements);
        truncateWorks();
    }

    @Test
    void unpublishedExcludedFromPublicListButDetailStillWorksWhenEntitled() {
        WorkRecord live = publish("live-game", WorkStatus.PUBLISHED, 0);
        WorkRecord down = publish("down-game", WorkStatus.UNPUBLISHED, 0);
        entitlements.grant(88L, down.id());

        WorkPageView publicList = query.list(88L, null, null, 1, 20);
        assertTrue(publicList.list().stream().anyMatch(item -> item.workCode().equals("live-game")));
        assertFalse(publicList.list().stream().anyMatch(item -> item.workCode().equals("down-game")));

        WorkPageView unlocked = query.list(88L, null, true, 1, 20);
        assertTrue(unlocked.list().stream().anyMatch(item -> item.workCode().equals("down-game")));

        WorkDetailView entitledDetail = query.getDetail(88L, "down-game");
        assertEquals(WorkStatus.UNPUBLISHED, entitledDetail.status());
        assertTrue(entitledDetail.unlocked());
        assertTrue(query.canAccessUnpublishedIfEntitled(88L, "down-game"));

        assertThrows(WorkUnpublishedException.class, () -> query.getDetail(99L, "down-game"));
        WorkDetailView liveDetail = query.getDetail(88L, "live-game");
        assertEquals(live.workCode(), liveDetail.workCode());
        assertFalse(liveDetail.unlocked());
    }

    @Test
    void grayVisibilityMatchesUniqueService() {
        WorkRecord gray = publish("gray-game", WorkStatus.GRAY, 25);
        WorkVisibilityService visibility = new WorkVisibilityService();
        long inside = 1L;
        while (!visibility.visibleInPublicCatalog(gray, inside)) {
            inside++;
        }
        long outside = 1L;
        while (visibility.visibleInPublicCatalog(gray, outside)) {
            outside++;
        }
        WorkPageView inPage = query.list(inside, null, null, 1, 20);
        WorkPageView outPage = query.list(outside, null, null, 1, 20);
        assertTrue(inPage.list().stream().anyMatch(item -> item.workCode().equals("gray-game")));
        assertFalse(outPage.list().stream().anyMatch(item -> item.workCode().equals("gray-game")));
        assertEquals("gray-game", query.getDetail(inside, "gray-game").workCode());
    }

    private WorkRecord publish(String code, WorkStatus terminal, int grayRatio) {
        WorkRecord created = catalog.create(new CreateWorkCommand(
                code, code, WorkType.game, "s", null, List.of(), "cat", List.of(), AccessForm.A,
                grayRatio, 1, "create", "op"));
        WorkRecord pending = catalog.update(transition(created, WorkStatus.PENDING_REVIEW));
        if (terminal == WorkStatus.UNPUBLISHED) {
            WorkRecord published = catalog.update(transition(pending, WorkStatus.PUBLISHED));
            return catalog.update(transition(published, WorkStatus.UNPUBLISHED));
        }
        return catalog.update(transition(pending, terminal));
    }

    private static UpdateWorkCommand transition(WorkRecord current, WorkStatus next) {
        return new UpdateWorkCommand(current.id(), current.name(), current.summary(), current.coverUrl(),
                current.screenshots(), current.category(), current.tags(), next, current.accessForm(),
                current.grayRatio(), current.sortWeight(), current.updateTime(), "status", "op");
    }
}
