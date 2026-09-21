package com.koujunxin.work.asset;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class InMemoryOssMetadataRepository implements OssMetadataRepository {

    private final ConcurrentMap<String, ObjectMetadata> rows = new ConcurrentHashMap<>();

    @Override
    public void upsert(ObjectMetadata metadata) {
        rows.put(id(metadata.scope(), metadata.objectKey()), metadata);
    }

    @Override
    public Optional<ObjectMetadata> find(StorageScope scope, String objectKey) {
        return Optional.ofNullable(rows.get(id(scope, objectKey)));
    }

    @Override
    public List<ObjectMetadata> listByWork(long workId, String version) {
        return rows.values().stream()
                .filter(row -> row.workId() != null && row.workId() == workId)
                .filter(row -> version == null || version.equals(row.version()))
                .toList();
    }

    private static String id(StorageScope scope, String key) {
        return scope.name() + ":" + key;
    }
}
