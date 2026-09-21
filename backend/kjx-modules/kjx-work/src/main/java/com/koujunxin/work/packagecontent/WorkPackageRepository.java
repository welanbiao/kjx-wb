package com.koujunxin.work.packagecontent;

import java.util.List;
import java.util.Optional;

public interface WorkPackageRepository {

    WorkPackageRecord upsert(WorkPackageRecord record);

    Optional<WorkPackageRecord> find(long workId, String version);

    List<WorkPackageRecord> listByWorkId(long workId);

    int update(WorkPackageRecord record);

    /**
     * 回写 WP-12 {@code kjx_work_version} 的包字段，不改仓库接口。
     */
    void touchVersionPackage(long workId, String version, String packageUrl, Long packageSize, String packageHash,
            String validateReportJson, int grayRatio);
}
