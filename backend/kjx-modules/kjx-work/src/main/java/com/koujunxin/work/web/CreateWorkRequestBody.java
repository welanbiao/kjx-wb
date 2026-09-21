package com.koujunxin.work.web;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CreateWorkRequestBody(
        String workCode,
        String name,
        String workType,
        String summary,
        String coverUrl,
        String category,
        List<String> tags,
        String accessForm,
        Integer grayRatio,
        Integer sortWeight,
        String reason
) {
}
