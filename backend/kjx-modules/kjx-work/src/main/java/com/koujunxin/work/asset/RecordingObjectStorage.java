package com.koujunxin.work.asset;

import java.time.Duration;
import java.util.Optional;

/**
 * 写对象时同步落 OSS 元数据。签名走本地 HMAC，不接触云密钥。
 */
public final class RecordingObjectStorage implements ObjectStoragePort {

    private final ObjectStoragePort delegate;
    private final OssMetadataRepository metadata;

    public RecordingObjectStorage(ObjectStoragePort delegate, OssMetadataRepository metadata) {
        this.delegate = delegate;
        this.metadata = metadata;
    }

    @Override
    public ObjectMetadata put(StorageScope scope, String objectKey, byte[] body, String contentType, Long workId,
            String version) {
        ObjectMetadata stored = delegate.put(scope, objectKey, body, contentType, workId, version);
        metadata.upsert(stored);
        return stored;
    }

    @Override
    public Optional<byte[]> get(StorageScope scope, String objectKey) {
        return delegate.get(scope, objectKey);
    }

    @Override
    public Optional<ObjectMetadata> metadata(StorageScope scope, String objectKey) {
        return metadata.find(scope, objectKey).or(() -> delegate.metadata(scope, objectKey));
    }

    @Override
    public SignedUrl sign(StorageScope scope, String objectKey, Duration ttl) {
        return delegate.sign(scope, objectKey, ttl);
    }
}
