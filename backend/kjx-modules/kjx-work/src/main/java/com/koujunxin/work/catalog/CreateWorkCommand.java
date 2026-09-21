package com.koujunxin.work.catalog;

import java.util.List;

public record CreateWorkCommand(
        String workCode,
        String name,
        WorkType workType,
        String summary,
        String coverUrl,
        List<String> screenshots,
        String category,
        List<String> tags,
        AccessForm accessForm,
        Integer grayRatio,
        Integer sortWeight,
        String reason,
        String actor
) {
}
