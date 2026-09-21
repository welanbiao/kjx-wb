package com.koujunxin.work.api;

import java.time.Instant;
import java.util.List;

import com.koujunxin.work.catalog.AccessForm;
import com.koujunxin.work.catalog.WorkRecord;
import com.koujunxin.work.catalog.WorkStatus;
import com.koujunxin.work.catalog.WorkType;

/**
 * 跨模块只读快照。不得携带 Entity/Mapper。
 */
public record WorkSnapshot(
        long id,
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
        List<String> allowedChannels,
        int grayRatio,
        int sortWeight,
        Instant publishTime,
        boolean deleted
) {

    public static WorkSnapshot from(WorkRecord record) {
        if (record == null || record.id() == null) {
            return null;
        }
        return new WorkSnapshot(
                record.id(),
                record.workCode(),
                record.name(),
                record.workType(),
                record.summary(),
                record.coverUrl(),
                record.screenshots(),
                record.category(),
                record.tags(),
                record.status(),
                record.currentVersion(),
                record.contentVersion(),
                record.accessForm(),
                record.allowedChannels(),
                record.grayRatio(),
                record.sortWeight(),
                record.publishTime(),
                record.deleted());
    }
}
