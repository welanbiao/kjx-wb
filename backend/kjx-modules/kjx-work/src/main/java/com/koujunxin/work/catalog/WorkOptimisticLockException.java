package com.koujunxin.work.catalog;

public class WorkOptimisticLockException extends WorkDomainException {

    public WorkOptimisticLockException(long workId) {
        super(409, "WORK_UPDATE_CONFLICT", "work was updated concurrently: " + workId);
        this.workId = workId;
    }

    private final long workId;

    public long workId() {
        return workId;
    }
}
