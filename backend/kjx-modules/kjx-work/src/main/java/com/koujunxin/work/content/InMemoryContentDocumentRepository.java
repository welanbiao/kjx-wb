package com.koujunxin.work.content;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class InMemoryContentDocumentRepository implements ContentDocumentRepository {

    private final AtomicLong ids = new AtomicLong(1);
    private final Map<String, ContentDocumentRecord> rows = new ConcurrentHashMap<>();

    @Override
    public ContentDocumentRecord upsert(ContentDocumentRecord record) {
        String key = key(record.workId(), record.version(), record.docType());
        ContentDocumentRecord existing = rows.get(key);
        long id = existing == null ? ids.getAndIncrement() : existing.id();
        ContentDocumentRecord saved = new ContentDocumentRecord(
                id, record.workId(), record.version(), record.docType(), record.contentJson(),
                record.nodeCount(), record.creator(), record.createTime());
        rows.put(key, saved);
        return saved;
    }

    @Override
    public Optional<ContentDocumentRecord> find(long workId, String version, String docType) {
        return Optional.ofNullable(rows.get(key(workId, version, docType)));
    }

    @Override
    public List<ContentDocumentRecord> list(long workId, String version) {
        return rows.values().stream()
                .filter(row -> row.workId() == workId && row.version().equals(version))
                .toList();
    }

    private static String key(long workId, String version, String docType) {
        return workId + ":" + version + ":" + docType;
    }

    public static String readJson(Path file) throws IOException {
        return Files.readString(file, StandardCharsets.UTF_8);
    }
}
