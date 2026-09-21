package com.koujunxin.work.catalog;

import java.time.Instant;

public record ContentVersionRecord(
        Long id,
        long workId,
        String version,
        String status,
        int nodeCount,
        String contentUrl,
        String contentJson,
        Instant reviewTime,
        Instant publishTime,
        String creator,
        Instant createTime
) {
}
