package com.koujunxin.work.catalog;

import java.time.Instant;
import java.util.List;

public record UpdateWorkCommand(
        long workId,
        String name,
        String summary,
        String coverUrl,
        List<String> screenshots,
        String category,
        List<String> tags,
        WorkStatus status,
        AccessForm accessForm,
        Integer grayRatio,
        Integer sortWeight,
        Instant expectedUpdateTime,
        String reason,
        String actor
) {
}
