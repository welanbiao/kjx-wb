package com.koujunxin.work.asset;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class InMemoryAssetRepository implements AssetRepository {

    private final AtomicLong ids = new AtomicLong(1);
    private final Map<String, AssetRecord> rows = new ConcurrentHashMap<>();

    @Override
    public AssetRecord upsert(AssetRecord record) {
        String key = pathKey(record.workId(), record.version(), record.path());
        AssetRecord existing = rows.get(key);
        long id = existing == null ? ids.getAndIncrement() : existing.id();
        AssetRecord saved = new AssetRecord(
                id, record.workId(), record.version(), record.assetKey(), record.path(), record.contentType(),
                record.sizeBytes(), record.sha256(), record.width(), record.height(), record.watermarkId(),
                record.storageKey(), record.scope(), record.tags(), record.license(), record.creator(),
                record.createTime() == null ? Instant.now() : record.createTime());
        rows.put(key, saved);
        return saved;
    }

    @Override
    public Optional<AssetRecord> findByPath(long workId, String version, String path) {
        return Optional.ofNullable(rows.get(pathKey(workId, version, path)));
    }

    @Override
    public Optional<AssetRecord> findByKey(long workId, String version, String assetKey) {
        return rows.values().stream()
                .filter(row -> row.workId() == workId && row.version().equals(version) && row.assetKey().equals(assetKey))
                .findFirst();
    }

    @Override
    public List<AssetRecord> list(long workId, String version) {
        return rows.values().stream()
                .filter(row -> row.workId() == workId && row.version().equals(version))
                .toList();
    }

    private static String pathKey(long workId, String version, String path) {
        return workId + ":" + version + ":" + path;
    }
}
