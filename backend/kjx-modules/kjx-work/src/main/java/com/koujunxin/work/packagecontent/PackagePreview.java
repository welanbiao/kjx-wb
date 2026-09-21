package com.koujunxin.work.packagecontent;

import java.time.Instant;
import java.util.Map;

public record PackagePreview(
        long workId,
        String version,
        String form,
        String previewToken,
        Instant expiresAt,
        Map<String, Object> layout,
        Map<String, Object> tokens,
        Map<String, Object> content,
        Map<String, String> assetUrls
) {
}
