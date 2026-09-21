package com.koujunxin.work.asset;

import java.time.Duration;
import java.util.Optional;

public interface ObjectStoragePort {

    ObjectMetadata put(StorageScope scope, String objectKey, byte[] body, String contentType, Long workId, String version);

    Optional<byte[]> get(StorageScope scope, String objectKey);

    Optional<ObjectMetadata> metadata(StorageScope scope, String objectKey);

    SignedUrl sign(StorageScope scope, String objectKey, Duration ttl);
}
