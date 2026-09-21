package com.koujunxin.work.catalog;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface WorkRepository {

    WorkRecord insert(WorkRecord record);

    Optional<WorkRecord> findById(long id);

    Optional<WorkRecord> findByCode(String workCode);

    /**
     * 按 {@code id + status + updateTime} CAS 更新。影响 0 行视为并发冲突或状态已变。
     */
    int updateCas(WorkRecord current, WorkRecord next);

    List<WorkRecord> listAdmin(AdminWorkQuery query);

    long countAdmin(AdminWorkQuery query);

    List<WorkRecord> listByStatuses(WorkStatus... statuses);

    List<WorkRecord> listByIds(Collection<Long> ids);
}
