package com.koujunxin.work.catalog;

import java.time.Instant;
import java.util.List;

/**
 * 作品主表快照。{@code currentVersion} 与 {@code contentVersion} 相互独立。
 */
public record WorkRecord(
        Long id,
        String workCode,
        String name,
        WorkType workType,
        String summary,
        String coverUrl,
        List<String> screenshots,
        String category,
        List<String> tags,
        WorkStatus status,
        String currentVersion,
        String contentVersion,
        AccessForm accessForm,
        String promptMode,
        List<String> allowedChannels,
        boolean allowByokDirect,
        List<String> apps,
        List<String> tools,
        List<String> serverTools,
        String pricingMode,
        int grayRatio,
        int sortWeight,
        int unlockCount,
        Instant publishTime,
        String creator,
        Instant createTime,
        String updater,
        Instant updateTime,
        boolean deleted
) {

    public WorkRecord {
        screenshots = screenshots == null ? List.of() : List.copyOf(screenshots);
        tags = tags == null ? List.of() : List.copyOf(tags);
        allowedChannels = allowedChannels == null || allowedChannels.isEmpty()
                ? List.of("PLATFORM", "BYOK") : List.copyOf(allowedChannels);
        apps = apps == null ? List.of() : List.copyOf(apps);
        tools = tools == null ? List.of() : List.copyOf(tools);
        serverTools = serverTools == null ? List.of() : List.copyOf(serverTools);
        promptMode = promptMode == null || promptMode.isBlank() ? "SERVER" : promptMode;
        accessForm = accessForm == null ? AccessForm.A : accessForm;
    }

    public WorkRecord withId(long newId) {
        return new WorkRecord(newId, workCode, name, workType, summary, coverUrl, screenshots, category, tags,
                status, currentVersion, contentVersion, accessForm, promptMode, allowedChannels, allowByokDirect,
                apps, tools, serverTools, pricingMode, grayRatio, sortWeight, unlockCount, publishTime,
                creator, createTime, updater, updateTime, deleted);
    }

    public WorkRecord withTimestamps(Instant created, Instant updated) {
        return new WorkRecord(id, workCode, name, workType, summary, coverUrl, screenshots, category, tags,
                status, currentVersion, contentVersion, accessForm, promptMode, allowedChannels, allowByokDirect,
                apps, tools, serverTools, pricingMode, grayRatio, sortWeight, unlockCount, publishTime,
                creator, created, updater, updated, deleted);
    }

    public WorkRecord withStatus(WorkStatus next, Instant nextPublishTime, String nextUpdater, Instant nextUpdateTime) {
        return new WorkRecord(id, workCode, name, workType, summary, coverUrl, screenshots, category, tags,
                next, currentVersion, contentVersion, accessForm, promptMode, allowedChannels, allowByokDirect,
                apps, tools, serverTools, pricingMode, grayRatio, sortWeight, unlockCount, nextPublishTime,
                creator, createTime, nextUpdater, nextUpdateTime, deleted);
    }

    public WorkRecord withVersions(String workVersion, String nextContentVersion) {
        return new WorkRecord(id, workCode, name, workType, summary, coverUrl, screenshots, category, tags,
                status, workVersion, nextContentVersion, accessForm, promptMode, allowedChannels, allowByokDirect,
                apps, tools, serverTools, pricingMode, grayRatio, sortWeight, unlockCount, publishTime,
                creator, createTime, updater, updateTime, deleted);
    }
}
