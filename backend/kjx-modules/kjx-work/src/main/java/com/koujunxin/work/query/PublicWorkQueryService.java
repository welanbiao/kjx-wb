package com.koujunxin.work.query;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.koujunxin.work.api.WorkDetailView;
import com.koujunxin.work.api.WorkPageView;
import com.koujunxin.work.api.WorkSnapshot;
import com.koujunxin.work.api.WorkSummaryView;
import com.koujunxin.work.catalog.WorkNotFoundException;
import com.koujunxin.work.catalog.WorkRecord;
import com.koujunxin.work.catalog.WorkRepository;
import com.koujunxin.work.catalog.WorkStatus;
import com.koujunxin.work.catalog.WorkType;
import com.koujunxin.work.catalog.WorkUnpublishedException;
import com.koujunxin.work.port.EntitlementQueryPort;
import com.koujunxin.work.visibility.WorkVisibilityService;

/**
 * C 端公开列表/详情。所有可见性判定只走 {@link WorkVisibilityService}。
 */
public class PublicWorkQueryService {

    private final WorkRepository works;
    private final WorkVisibilityService visibility;
    private final EntitlementQueryPort entitlements;

    public PublicWorkQueryService(
            WorkRepository works,
            WorkVisibilityService visibility,
            EntitlementQueryPort entitlements) {
        this.works = works;
        this.visibility = visibility;
        this.entitlements = entitlements;
    }

    public WorkPageView list(long accountId, WorkType workType, Boolean unlocked, int pageNo, int pageSize) {
        int safePageNo = Math.max(pageNo, 1);
        int safePageSize = Math.min(Math.max(pageSize, 1), 100);
        List<WorkRecord> candidates = loadCandidates(accountId, Boolean.TRUE.equals(unlocked));
        Set<Long> entitledIds = entitlements.findEntitledWorkIds(accountId);
        List<WorkSummaryView> visible = new ArrayList<>();
        for (WorkRecord work : candidates) {
            if (workType != null && work.workType() != workType) {
                continue;
            }
            boolean entitled = entitledIds.contains(work.id());
            if (!passesListVisibility(work, accountId, entitled, unlocked)) {
                continue;
            }
            if (unlocked != null && entitled != unlocked) {
                continue;
            }
            visible.add(toSummary(work, entitled));
        }
        visible = sortRecords(candidates, visible);
        int from = Math.min((safePageNo - 1) * safePageSize, visible.size());
        int to = Math.min(from + safePageSize, visible.size());
        return new WorkPageView(List.copyOf(visible.subList(from, to)), visible.size());
    }

    public WorkDetailView getDetail(long accountId, String workCode) {
        WorkRecord work = works.findByCode(workCode).orElseThrow(() -> new WorkNotFoundException(workCode));
        boolean entitled = entitlements.hasEntitlement(accountId, work.id());
        WorkSnapshot snapshot = WorkSnapshot.from(work);
        if (visibility.canViewDetail(snapshot, accountId, entitled)) {
            return toDetail(work, entitled);
        }
        if (work.status() == WorkStatus.UNPUBLISHED) {
            throw new WorkUnpublishedException();
        }
        throw new WorkNotFoundException(workCode);
    }

    /**
     * 供 WP-13：已解锁下架作品是否仍可访问详情。不实现解锁。
     */
    public boolean canAccessUnpublishedIfEntitled(long accountId, String workCode) {
        return works.findByCode(workCode)
                .map(work -> visibility.canViewDetail(
                        WorkSnapshot.from(work),
                        accountId,
                        entitlements.hasEntitlement(accountId, work.id())))
                .orElse(false);
    }

    private List<WorkRecord> loadCandidates(long accountId, boolean includeEntitledUnpublished) {
        Map<Long, WorkRecord> byId = new HashMap<>();
        for (WorkRecord work : works.listByStatuses(WorkStatus.PUBLISHED, WorkStatus.GRAY)) {
            byId.put(work.id(), work);
        }
        if (includeEntitledUnpublished) {
            Set<Long> entitledIds = entitlements.findEntitledWorkIds(accountId);
            for (WorkRecord work : works.listByIds(entitledIds)) {
                byId.putIfAbsent(work.id(), work);
            }
        }
        return new ArrayList<>(byId.values());
    }

    private boolean passesListVisibility(WorkRecord work, long accountId, boolean entitled, Boolean unlockedFilter) {
        if (Boolean.TRUE.equals(unlockedFilter)) {
            return visibility.canViewDetail(work, accountId, entitled);
        }
        return visibility.visibleInPublicCatalog(work, accountId);
    }

    private static List<WorkSummaryView> sortRecords(List<WorkRecord> source, List<WorkSummaryView> views) {
        Map<String, WorkRecord> byCode = new HashMap<>();
        for (WorkRecord record : source) {
            byCode.put(record.workCode(), record);
        }
        views.sort(Comparator
                .comparingInt((WorkSummaryView view) -> byCode.get(view.workCode()).sortWeight()).reversed()
                .thenComparing((WorkSummaryView view) -> byCode.get(view.workCode()).publishTime(),
                        Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing((WorkSummaryView view) -> byCode.get(view.workCode()).id(), Comparator.reverseOrder()));
        return views;
    }

    private static WorkSummaryView toSummary(WorkRecord work, boolean entitled) {
        return new WorkSummaryView(
                work.workCode(),
                work.name(),
                work.workType(),
                work.summary(),
                work.coverUrl(),
                work.category(),
                work.tags(),
                work.status(),
                work.currentVersion(),
                work.accessForm(),
                entitled,
                null);
    }

    private static WorkDetailView toDetail(WorkRecord work, boolean entitled) {
        return new WorkDetailView(
                work.workCode(),
                work.name(),
                work.workType(),
                work.summary(),
                work.coverUrl(),
                work.category(),
                work.tags(),
                work.status(),
                work.currentVersion(),
                work.accessForm(),
                entitled,
                null,
                work.screenshots(),
                work.allowedChannels(),
                work.contentVersion(),
                entitled ? "/runtime/" + work.workCode() : null,
                work.publishTime());
    }
}
