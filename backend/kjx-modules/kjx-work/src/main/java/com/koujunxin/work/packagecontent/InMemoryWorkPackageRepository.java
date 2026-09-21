package com.koujunxin.work.packagecontent;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class InMemoryWorkPackageRepository implements WorkPackageRepository {

    private final AtomicLong ids = new AtomicLong(1);
    private final Map<String, WorkPackageRecord> rows = new ConcurrentHashMap<>();

    @Override
    public WorkPackageRecord upsert(WorkPackageRecord record) {
        String key = key(record.workId(), record.version());
        WorkPackageRecord existing = rows.get(key);
        long id = existing == null ? ids.getAndIncrement() : existing.id();
        WorkPackageRecord saved = new WorkPackageRecord(
                id, record.workId(), record.version(), record.status(), record.form(), record.packageHash(),
                record.packageSize(), record.storageKey(), record.cdnPrefix(), record.serverPrefix(),
                record.contentVersion(), record.grayRatio(), record.previousVersion(), record.validateReportJson(),
                record.previewToken(), record.creator(),
                record.createTime() == null ? Instant.now() : record.createTime());
        rows.put(key, saved);
        return saved;
    }

    @Override
    public Optional<WorkPackageRecord> find(long workId, String version) {
        return Optional.ofNullable(rows.get(key(workId, version)));
    }

    @Override
    public List<WorkPackageRecord> listByWorkId(long workId) {
        return rows.values().stream().filter(row -> row.workId() == workId).toList();
    }

    @Override
    public int update(WorkPackageRecord record) {
        rows.put(key(record.workId(), record.version()), record);
        return 1;
    }

    @Override
    public void touchVersionPackage(long workId, String version, String packageUrl, Long packageSize,
            String packageHash, String validateReportJson, int grayRatio) {
        // in-memory catalog 不持有这些列；发布路径走 WorkCatalogService.activateWorkVersion
    }

    private static String key(long workId, String version) {
        return workId + ":" + version;
    }
}
