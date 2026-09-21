package com.koujunxin.work.catalog;

public class DuplicateWorkCodeException extends WorkDomainException {

    public DuplicateWorkCodeException(String workCode) {
        super(409, "DUPLICATE_WORK_CODE", "workCode already exists: " + workCode);
        this.workCode = workCode;
    }

    private final String workCode;

    public String workCode() {
        return workCode;
    }
}
