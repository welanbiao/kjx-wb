package com.koujunxin.work.catalog;

import java.util.List;
import java.util.Optional;

public interface WorkVersionRepository {

    WorkVersionRecord insert(WorkVersionRecord record);

    Optional<WorkVersionRecord> find(long workId, String version);

    List<WorkVersionRecord> listByWorkId(long workId);

    int updateStatus(long workId, String version, String fromStatus, String toStatus);
}
