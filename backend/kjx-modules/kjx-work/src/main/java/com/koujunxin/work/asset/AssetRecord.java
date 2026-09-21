package com.koujunxin.work.asset;

import java.time.Instant;

public record AssetRecord(
        Long id,
        long workId,
        String version,
        String assetKey,
        String path,
        String contentType,
        long sizeBytes,
        String sha256,
        Integer width,
        Integer height,
        String watermarkId,
        String storageKey,
        StorageScope scope,
        String tags,
        String license,
        String creator,
        Instant createTime
) {
}
