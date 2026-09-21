package com.koujunxin.runtime.api;

import java.time.Instant;

/**
 * 短时效 CDN 签名。不得指向 server/ 对象。
 */
public record SignedAsset(String assetKey, String url, Instant expiresAt) {

    public SignedAsset {
        if (assetKey == null || assetKey.isBlank()) {
            throw new IllegalArgumentException("assetKey is required");
        }
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("url is required");
        }
        if (expiresAt == null) {
            throw new IllegalArgumentException("expiresAt is required");
        }
        if (url.toLowerCase().contains("/server/")) {
            throw new IllegalArgumentException("server/ assets cannot be signed");
        }
    }
}
