package com.koujunxin.work.catalog;

public record CreateContentVersionCommand(
        long workId,
        String version,
        int nodeCount,
        String contentUrl,
        String contentJson,
        String actor
) {
}
