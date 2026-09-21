package com.koujunxin.work.asset.web;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.koujunxin.work.asset.AssetRecord;
import com.koujunxin.work.asset.AssetService;
import com.koujunxin.work.asset.SignedUrl;
import com.koujunxin.work.web.ApiEnvelope;

@RestController
@RequestMapping("/admin-api/kjx/work/{workId}/versions/{version}/assets")
@ConditionalOnBean(AssetService.class)
public class AdminAssetController {

    private final AssetService assets;

    public AdminAssetController(AssetService assets) {
        this.assets = assets;
    }

    @GetMapping
    public ApiEnvelope<List<AssetRecord>> list(
            @PathVariable long workId,
            @PathVariable String version,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId) {
        return ApiEnvelope.ok(requestId, assets.list(workId, version));
    }

    @PostMapping("/{assetKey}/sign")
    public ApiEnvelope<Map<String, Object>> sign(
            @PathVariable long workId,
            @PathVariable String version,
            @PathVariable String assetKey,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId) {
        SignedUrl signed = assets.sign(workId, version, assetKey);
        return ApiEnvelope.ok(requestId, Map.of(
                "objectKey", signed.objectKey(),
                "expiresAt", signed.expiresAt().toString(),
                "ttlSeconds", Duration.ofMinutes(10).toSeconds(),
                "url", signed.url()));
    }
}
