package com.koujunxin.work.content;

import java.time.Instant;

public record ContentDocumentRecord(
        Long id,
        long workId,
        String version,
        String docType,
        String contentJson,
        int nodeCount,
        String creator,
        Instant createTime
) {
}
