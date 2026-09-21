package com.koujunxin.work.catalog;

public class WorkNotFoundException extends WorkDomainException {

    public WorkNotFoundException(String workCode) {
        super(404, "WORK_NOT_FOUND", "work not found: " + workCode);
        this.workCode = workCode;
        this.workId = null;
    }

    public WorkNotFoundException(long workId) {
        super(404, "WORK_NOT_FOUND", "work not found: " + workId);
        this.workCode = null;
        this.workId = workId;
    }

    private final String workCode;
    private final Long workId;

    public String workCode() {
        return workCode;
    }

    public Long workId() {
        return workId;
    }
}
