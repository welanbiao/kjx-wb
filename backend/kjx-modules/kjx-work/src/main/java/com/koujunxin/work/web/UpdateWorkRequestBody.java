package com.koujunxin.work.web;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record UpdateWorkRequestBody(
        String name,
        String summary,
        String coverUrl,
        String category,
        List<String> tags,
        String status,
        String accessForm,
        Integer grayRatio,
        Integer sortWeight,
        String reason
) {
}
