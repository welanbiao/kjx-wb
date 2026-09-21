package com.koujunxin.work.asset;

import java.time.Instant;

/**
 * 签名 URL。{@link #toString()} 故意不包含 URL，避免日志带出签名凭证。
 */
public record SignedUrl(String url, Instant expiresAt, String objectKey, StorageScope scope) {

    @Override
    public String toString() {
        return "SignedUrl{scope=" + scope + ", key=" + objectKey + ", expiresAt=" + expiresAt + ", url=REDACTED}";
    }
}
