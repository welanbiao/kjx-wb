package com.koujunxin.work.catalog;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class InMemoryWorkRepository implements WorkRepository {

    private final AtomicLong ids = new AtomicLong(1);
    private final Map<Long, WorkRecord> byId = new ConcurrentHashMap<>();

    @Override
    public WorkRecord insert(WorkRecord record) {
        if (byId.values().stream().anyMatch(existing -> existing.workCode().equals(record.workCode()))) {
            throw new DuplicateWorkCodeException(record.workCode());
        }
        Instant now = Instant.now();
        WorkRecord saved = record.withId(ids.getAndIncrement()).withTimestamps(now, now);
        byId.put(saved.id(), saved);
        return saved;
    }

    @Override
    public Optional<WorkRecord> findById(long id) {
        return Optional.ofNullable(byId.get(id)).filter(work -> !work.deleted());
    }

    @Override
    public Optional<WorkRecord> findByCode(String workCode) {
        return byId.values().stream()
                .filter(work -> !work.deleted() && work.workCode().equals(workCode))
                .findFirst();
    }

    @Override
    public int updateCas(WorkRecord current, WorkRecord next) {
        WorkRecord existing = byId.get(current.id());
        if (existing == null || existing.deleted()) {
            return 0;
        }
        if (existing.status() != current.status()) {
            return 0;
        }
        if (existing.updateTime() != null && current.updateTime() != null
                && !existing.updateTime().equals(current.updateTime())) {
            return 0;
        }
        Instant now = Instant.now();
        if (now.equals(existing.updateTime())) {
            now = now.plusMillis(1);
        }
        WorkRecord saved = next.withTimestamps(existing.createTime(), now);
        byId.put(saved.id(), saved);
        return 1;
    }

    @Override
    public List<WorkRecord> listAdmin(AdminWorkQuery query) {
        return listAll().stream()
                .filter(work -> query.workType() == null || work.workType() == query.workType())
                .filter(work -> query.status() == null || work.status() == query.status())
                .filter(work -> query.keyword() == null
                        || work.name().contains(query.keyword())
                        || work.workCode().contains(query.keyword()))
                .sorted(Comparator.comparingInt(WorkRecord::sortWeight).reversed().thenComparing(WorkRecord::id, Comparator.reverseOrder()))
                .skip(query.offset())
                .limit(query.pageSize())
                .toList();
    }

    @Override
    public long countAdmin(AdminWorkQuery query) {
        return listAll().stream()
                .filter(work -> query.workType() == null || work.workType() == query.workType())
                .filter(work -> query.status() == null || work.status() == query.status())
                .filter(work -> query.keyword() == null
                        || work.name().contains(query.keyword())
                        || work.workCode().contains(query.keyword()))
                .count();
    }

    @Override
    public List<WorkRecord> listByStatuses(WorkStatus... statuses) {
        List<WorkStatus> allowed = statuses == null ? List.of() : List.of(statuses);
        return listAll().stream()
                .filter(work -> allowed.contains(work.status()))
                .sorted(Comparator.comparingInt(WorkRecord::sortWeight).reversed()
                        .thenComparing(WorkRecord::publishTime, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(WorkRecord::id, Comparator.reverseOrder()))
                .toList();
    }

    @Override
    public List<WorkRecord> listByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<WorkRecord> result = new ArrayList<>();
        for (Long id : ids) {
            findById(id).ifPresent(result::add);
        }
        return result;
    }

    private List<WorkRecord> listAll() {
        return byId.values().stream().filter(work -> !work.deleted()).toList();
    }
}
