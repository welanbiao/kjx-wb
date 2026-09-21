package com.koujunxin.work.asset;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.koujunxin.work.packagecontent.CdnIsolation;
import com.koujunxin.work.packagecontent.IsolatedZipExtractor;
import com.koujunxin.work.packagecontent.StreamingUploads;
import com.koujunxin.work.packagecontent.WorkPackageException;

public final class AssetService {

    public static final Duration DEFAULT_TTL = Duration.ofMinutes(10);

    private final AssetRepository assets;
    private final ObjectStoragePort storage;

    public AssetService(AssetRepository assets, ObjectStoragePort storage) {
        this.assets = assets;
        this.storage = storage;
    }

    public void ingestPackageFiles(long workId, String version, Path root, List<String> files, String actor)
            throws IOException {
        for (String relative : files) {
            if (!relative.startsWith("assets/") || relative.equals("assets/assets.manifest.json")) {
                continue;
            }
            Path file = IsolatedZipExtractor.safeResolve(root, relative);
            byte[] body = Files.readAllBytes(file);
            StorageScope scope = StorageScope.CDN;
            String objectKey = objectKey(workId, version, relative);
            CdnIsolation.rejectCdnKey(objectKey);
            ObjectMetadata meta = storage.put(scope, objectKey, body, contentType(relative), workId, version);
            String assetKey = assetKey(relative);
            assets.upsert(new AssetRecord(
                    null, workId, version, assetKey, relative, meta.contentType(), meta.sizeBytes(), meta.sha256(),
                    null, null, null, objectKey, scope, null, "package", actor, Instant.now()));
        }
    }

    public SignedUrl sign(long workId, String version, String assetKey) {
        AssetRecord asset = assets.findByKey(workId, version, assetKey)
                .orElseThrow(() -> WorkPackageException.notFound("asset not found: " + assetKey));
        if (asset.scope() != StorageScope.CDN) {
            throw WorkPackageException.badRequest("SERVER_NOT_ON_CDN", "asset is not CDN-deliverable");
        }
        CdnIsolation.rejectCdnKey(asset.storageKey());
        return storage.sign(StorageScope.CDN, asset.storageKey(), DEFAULT_TTL);
    }

    public Map<String, String> previewUrls(long workId, String version) {
        Map<String, String> urls = new LinkedHashMap<>();
        for (AssetRecord asset : assets.list(workId, version)) {
            if (asset.scope() != StorageScope.CDN) {
                continue;
            }
            SignedUrl signed = storage.sign(StorageScope.CDN, asset.storageKey(), DEFAULT_TTL);
            urls.put(asset.path(), signed.url());
        }
        return urls;
    }

    public List<AssetRecord> list(long workId, String version) {
        return assets.list(workId, version);
    }

    static String objectKey(long workId, String version, String relative) {
        return "works/" + workId + "/" + version + "/cdn/" + relative;
    }

    static String assetKey(String relative) {
        String name = relative.substring(relative.lastIndexOf('/') + 1);
        int dot = name.lastIndexOf('.');
        return StreamingUploads.hex(relative.getBytes(java.nio.charset.StandardCharsets.UTF_8)).substring(0, 12)
                + "-" + (dot < 0 ? name : name.substring(0, dot));
    }

    static String contentType(String relative) {
        String lower = relative.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".png")) {
            return "image/png";
        }
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (lower.endsWith(".webp")) {
            return "image/webp";
        }
        if (lower.endsWith(".svg")) {
            return "image/svg+xml";
        }
        if (lower.endsWith(".mp3")) {
            return "audio/mpeg";
        }
        if (lower.endsWith(".ogg")) {
            return "audio/ogg";
        }
        if (lower.endsWith(".woff2")) {
            return "font/woff2";
        }
        if (lower.endsWith(".mp4")) {
            return "video/mp4";
        }
        if (lower.endsWith(".webm")) {
            return "video/webm";
        }
        return "application/octet-stream";
    }
}
