package com.koujunxin.work.asset;

import java.time.Instant;

public record ObjectMetadata(
        StorageScope scope,
        String objectKey,
        long sizeBytes,
        String sha256,
        String contentType,
        Long workId,
        String version,
        Instant createdAt
) {
}
