package com.koujunxin.work.asset;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import com.koujunxin.work.packagecontent.StreamingUploads;
import com.koujunxin.work.packagecontent.WorkPackageException;

public final class InMemoryObjectStorage implements ObjectStoragePort {

    private final Map<String, Stored> objects = new ConcurrentHashMap<>();
    private final HmacSignedUrlSigner signer;

    public InMemoryObjectStorage(HmacSignedUrlSigner signer) {
        this.signer = signer;
    }

    @Override
    public ObjectMetadata put(StorageScope scope, String objectKey, byte[] body, String contentType, Long workId,
            String version) {
        byte[] copy = body == null ? new byte[0] : body.clone();
        ObjectMetadata meta = new ObjectMetadata(
                scope, objectKey, copy.length, StreamingUploads.hex(copy), contentType, workId, version, Instant.now());
        objects.put(id(scope, objectKey), new Stored(copy, meta));
        return meta;
    }

    @Override
    public Optional<byte[]> get(StorageScope scope, String objectKey) {
        Stored stored = objects.get(id(scope, objectKey));
        return stored == null ? Optional.empty() : Optional.of(stored.body.clone());
    }

    @Override
    public Optional<ObjectMetadata> metadata(StorageScope scope, String objectKey) {
        Stored stored = objects.get(id(scope, objectKey));
        return stored == null ? Optional.empty() : Optional.of(stored.meta);
    }

    @Override
    public SignedUrl sign(StorageScope scope, String objectKey, Duration ttl) {
        if (!objects.containsKey(id(scope, objectKey))) {
            throw WorkPackageException.notFound("object not found: " + objectKey);
        }
        return signer.sign(scope, objectKey, ttl);
    }

    public HmacSignedUrlSigner signer() {
        return signer;
    }

    private static String id(StorageScope scope, String key) {
        return scope.name() + ":" + key;
    }

    private record Stored(byte[] body, ObjectMetadata meta) {
    }
}
