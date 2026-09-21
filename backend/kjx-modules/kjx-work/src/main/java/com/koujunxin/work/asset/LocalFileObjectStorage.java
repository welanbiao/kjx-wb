package com.koujunxin.work.asset;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import com.koujunxin.work.packagecontent.StreamingUploads;
import com.koujunxin.work.packagecontent.WorkPackageException;

public final class LocalFileObjectStorage implements ObjectStoragePort {

    private final Path root;
    private final HmacSignedUrlSigner signer;
    private final Map<String, ObjectMetadata> metadata = new ConcurrentHashMap<>();

    public LocalFileObjectStorage(Path root, HmacSignedUrlSigner signer) {
        this.root = root;
        this.signer = signer;
    }

    @Override
    public ObjectMetadata put(StorageScope scope, String objectKey, byte[] body, String contentType, Long workId,
            String version) {
        try {
            Path dest = resolve(scope, objectKey);
            Files.createDirectories(dest.getParent());
            byte[] copy = body == null ? new byte[0] : body;
            Files.write(dest, copy);
            ObjectMetadata meta = new ObjectMetadata(
                    scope, objectKey, copy.length, StreamingUploads.hex(copy), contentType, workId, version,
                    Instant.now());
            metadata.put(id(scope, objectKey), meta);
            return meta;
        } catch (IOException ex) {
            throw new WorkPackageException(500, "OSS_PUT_FAILED", "failed to store object");
        }
    }

    @Override
    public Optional<byte[]> get(StorageScope scope, String objectKey) {
        Path dest = resolve(scope, objectKey);
        if (!Files.isRegularFile(dest)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Files.readAllBytes(dest));
        } catch (IOException ex) {
            throw new WorkPackageException(500, "OSS_GET_FAILED", "failed to read object");
        }
    }

    @Override
    public Optional<ObjectMetadata> metadata(StorageScope scope, String objectKey) {
        return Optional.ofNullable(metadata.get(id(scope, objectKey)));
    }

    @Override
    public SignedUrl sign(StorageScope scope, String objectKey, Duration ttl) {
        if (get(scope, objectKey).isEmpty()) {
            throw WorkPackageException.notFound("object not found: " + objectKey);
        }
        return signer.sign(scope, objectKey, ttl);
    }

    private Path resolve(StorageScope scope, String objectKey) {
        String safe = objectKey.replace('\\', '/');
        if (safe.startsWith("/") || safe.contains(":") || safe.contains("..")) {
            throw WorkPackageException.badRequest("OSS_BAD_KEY", "invalid object key");
        }
        Path base = root.resolve(scope.name().toLowerCase()).toAbsolutePath().normalize();
        Path dest = base.resolve(safe).normalize();
        if (!dest.startsWith(base)) {
            throw WorkPackageException.badRequest("OSS_BAD_KEY", "invalid object key");
        }
        return dest;
    }

    private static String id(StorageScope scope, String key) {
        return scope.name() + ":" + key;
    }
}
