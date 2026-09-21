package com.koujunxin.work.catalog;

import java.util.List;
import java.util.Optional;

public interface ContentVersionRepository {

    ContentVersionRecord insert(ContentVersionRecord record);

    Optional<ContentVersionRecord> find(long workId, String version);

    List<ContentVersionRecord> listByWorkId(long workId);

    int updateStatus(long workId, String version, String fromStatus, String toStatus);
}
