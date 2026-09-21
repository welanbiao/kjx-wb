package com.koujunxin.work.catalog;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class InMemoryWorkVersionRepository implements WorkVersionRepository {

    private final AtomicLong ids = new AtomicLong(1);
    private final Map<String, WorkVersionRecord> rows = new ConcurrentHashMap<>();

    @Override
    public WorkVersionRecord insert(WorkVersionRecord record) {
        String key = record.workId() + ":" + record.version();
        if (rows.containsKey(key)) {
            throw new DuplicateVersionException(record.workId(), record.version());
        }
        WorkVersionRecord saved = new WorkVersionRecord(
                ids.getAndIncrement(), record.workId(), record.version(), record.status(), record.form(),
                record.runtimeCompat(), record.entry(), record.packageUrl(), record.packageSize(),
                record.packageHash(), record.grayRatio(), record.publishTime(), record.creator(), Instant.now());
        rows.put(key, saved);
        return saved;
    }

    @Override
    public Optional<WorkVersionRecord> find(long workId, String version) {
        return Optional.ofNullable(rows.get(workId + ":" + version));
    }

    @Override
    public List<WorkVersionRecord> listByWorkId(long workId) {
        return rows.values().stream().filter(row -> row.workId() == workId).toList();
    }

    @Override
    public int updateStatus(long workId, String version, String fromStatus, String toStatus) {
        WorkVersionRecord current = rows.get(workId + ":" + version);
        if (current == null || !current.status().equals(fromStatus)) {
            return 0;
        }
        WorkVersionRecord next = new WorkVersionRecord(
                current.id(), current.workId(), current.version(), toStatus, current.form(),
                current.runtimeCompat(), current.entry(), current.packageUrl(), current.packageSize(),
                current.packageHash(), current.grayRatio(), Instant.now(), current.creator(), current.createTime());
        rows.put(workId + ":" + version, next);
        return 1;
    }
}
