package com.koujunxin.work.web;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

import com.koujunxin.work.api.WorkDetailView;
import com.koujunxin.work.api.WorkPageView;
import com.koujunxin.work.api.WorkSummaryView;
import com.koujunxin.work.catalog.WorkRecord;

public final class WorkApiMapper {

    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
            .withZone(SHANGHAI);

    private WorkApiMapper() {
    }

    public static String formatTime(Instant instant) {
        return instant == null ? null : ISO.format(instant);
    }

    public static AdminWorkBody toAdmin(WorkRecord work) {
        return new AdminWorkBody(
                work.id(),
                work.workCode(),
                work.name(),
                work.workType().name(),
                work.summary(),
                work.coverUrl(),
                work.category(),
                work.tags(),
                work.status().name(),
                work.currentVersion(),
                work.contentVersion(),
                work.accessForm().name(),
                work.grayRatio(),
                work.sortWeight(),
                formatTime(work.publishTime()),
                formatTime(work.createTime()));
    }

    public static WorkSummaryBody toSummary(WorkSummaryView view) {
        return new WorkSummaryBody(
                view.workCode(),
                view.name(),
                view.workType().name(),
                view.summary(),
                view.coverUrl(),
                view.category(),
                view.tags(),
                view.status().name(),
                view.currentVersion(),
                view.accessForm().name(),
                view.unlocked(),
                formatTime(view.unlockedAt()));
    }

    public static WorkDetailBody toDetail(WorkDetailView view) {
        return new WorkDetailBody(
                view.workCode(),
                view.name(),
                view.workType().name(),
                view.summary(),
                view.coverUrl(),
                view.category(),
                view.tags(),
                view.status().name(),
                view.currentVersion(),
                view.accessForm().name(),
                view.unlocked(),
                formatTime(view.unlockedAt()),
                view.screenshots(),
                view.allowedChannels(),
                view.contentVersion(),
                view.entryUrl(),
                formatTime(view.publishTime()));
    }

    public static WorkPageBody toPage(WorkPageView page) {
        return new WorkPageBody(page.list().stream().map(WorkApiMapper::toSummary).toList(), page.total());
    }

    public record AdminWorkBody(
            long id,
            String workCode,
            String name,
            String workType,
            String summary,
            String coverUrl,
            String category,
            List<String> tags,
            String status,
            String currentVersion,
            String contentVersion,
            String accessForm,
            int grayRatio,
            int sortWeight,
            String publishTime,
            String createTime
    ) {
    }

    public record AdminWorkPageBody(List<AdminWorkBody> list, long total) {
    }

    public record WorkSummaryBody(
            String workCode,
            String name,
            String workType,
            String summary,
            String coverUrl,
            String category,
            List<String> tags,
            String status,
            String currentVersion,
            String accessForm,
            boolean unlocked,
            String unlockedAt
    ) {
    }

    public record WorkDetailBody(
            String workCode,
            String name,
            String workType,
            String summary,
            String coverUrl,
            String category,
            List<String> tags,
            String status,
            String currentVersion,
            String accessForm,
            boolean unlocked,
            String unlockedAt,
            List<String> screenshots,
            List<String> allowedChannels,
            String contentVersion,
            String entryUrl,
            String publishTime
    ) {
    }

    public record WorkPageBody(List<WorkSummaryBody> list, long total) {
    }
}
