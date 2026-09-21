package com.koujunxin.work.catalog;

import java.time.Instant;

public record WorkVersionRecord(
        Long id,
        long workId,
        String version,
        String status,
        String form,
        String runtimeCompat,
        String entry,
        String packageUrl,
        Long packageSize,
        String packageHash,
        int grayRatio,
        Instant publishTime,
        String creator,
        Instant createTime
) {
}
