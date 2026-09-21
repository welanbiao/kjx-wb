package com.koujunxin.work.catalog;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class InMemoryContentVersionRepository implements ContentVersionRepository {

    private final AtomicLong ids = new AtomicLong(1);
    private final Map<String, ContentVersionRecord> rows = new ConcurrentHashMap<>();

    @Override
    public ContentVersionRecord insert(ContentVersionRecord record) {
        String key = record.workId() + ":" + record.version();
        if (rows.containsKey(key)) {
            throw new DuplicateVersionException(record.workId(), record.version());
        }
        ContentVersionRecord saved = new ContentVersionRecord(
                ids.getAndIncrement(), record.workId(), record.version(), record.status(), record.nodeCount(),
                record.contentUrl(), record.contentJson(), record.reviewTime(), record.publishTime(),
                record.creator(), Instant.now());
        rows.put(key, saved);
        return saved;
    }

    @Override
    public Optional<ContentVersionRecord> find(long workId, String version) {
        return Optional.ofNullable(rows.get(workId + ":" + version));
    }

    @Override
    public List<ContentVersionRecord> listByWorkId(long workId) {
        return rows.values().stream().filter(row -> row.workId() == workId).toList();
    }

    @Override
    public int updateStatus(long workId, String version, String fromStatus, String toStatus) {
        ContentVersionRecord current = rows.get(workId + ":" + version);
        if (current == null || !current.status().equals(fromStatus)) {
            return 0;
        }
        ContentVersionRecord next = new ContentVersionRecord(
                current.id(), current.workId(), current.version(), toStatus, current.nodeCount(),
                current.contentUrl(), current.contentJson(), current.reviewTime(), Instant.now(),
                current.creator(), current.createTime());
        rows.put(workId + ":" + version, next);
        return 1;
    }
}
