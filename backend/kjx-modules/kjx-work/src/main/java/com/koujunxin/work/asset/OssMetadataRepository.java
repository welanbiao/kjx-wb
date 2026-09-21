package com.koujunxin.work.asset;

import java.util.List;
import java.util.Optional;

public interface OssMetadataRepository {

    void upsert(ObjectMetadata metadata);

    Optional<ObjectMetadata> find(StorageScope scope, String objectKey);

    List<ObjectMetadata> listByWork(long workId, String version);
}
