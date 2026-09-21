package com.koujunxin.work.packagecontent;

import java.time.Instant;

public record WorkPackageRecord(
        Long id,
        long workId,
        String version,
        WorkPackageStatus status,
        String form,
        String packageHash,
        Long packageSize,
        String storageKey,
        String cdnPrefix,
        String serverPrefix,
        String contentVersion,
        int grayRatio,
        String previousVersion,
        String validateReportJson,
        String previewToken,
        String creator,
        Instant createTime
) {
    public WorkPackageRecord withStatus(WorkPackageStatus next, String updaterIgnored) {
        return new WorkPackageRecord(
                id, workId, version, next, form, packageHash, packageSize, storageKey, cdnPrefix, serverPrefix,
                contentVersion, grayRatio, previousVersion, validateReportJson, previewToken, creator, createTime);
    }

    public WorkPackageRecord withRelease(String previous, int gray, WorkPackageStatus next) {
        return new WorkPackageRecord(
                id, workId, version, next, form, packageHash, packageSize, storageKey, cdnPrefix, serverPrefix,
                contentVersion, gray, previous, validateReportJson, previewToken, creator, createTime);
    }

    public WorkPackageRecord withPreview(String token) {
        return new WorkPackageRecord(
                id, workId, version, WorkPackageStatus.PREVIEWED, form, packageHash, packageSize, storageKey,
                cdnPrefix, serverPrefix, contentVersion, grayRatio, previousVersion, validateReportJson, token,
                creator, createTime);
    }

    public WorkPackageRecord withReport(WorkPackageStatus next, String reportJson) {
        return new WorkPackageRecord(
                id, workId, version, next, form, packageHash, packageSize, storageKey, cdnPrefix, serverPrefix,
                contentVersion, grayRatio, previousVersion, reportJson, previewToken, creator, createTime);
    }
}
