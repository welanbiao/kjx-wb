package com.koujunxin.work.api;

import java.time.Instant;
import java.util.List;

import com.koujunxin.work.catalog.AccessForm;
import com.koujunxin.work.catalog.WorkStatus;
import com.koujunxin.work.catalog.WorkType;

public record WorkDetailView(
        String workCode,
        String name,
        WorkType workType,
        String summary,
        String coverUrl,
        String category,
        List<String> tags,
        WorkStatus status,
        String currentVersion,
        AccessForm accessForm,
        boolean unlocked,
        Instant unlockedAt,
        List<String> screenshots,
        List<String> allowedChannels,
        String contentVersion,
        String entryUrl,
        Instant publishTime
) {
}
