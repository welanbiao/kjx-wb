package com.koujunxin.work.catalog;

public class WorkDomainException extends RuntimeException {

    private final int httpStatus;
    private final String errorName;

    public WorkDomainException(int httpStatus, String errorName, String message) {
        super(message);
        this.httpStatus = httpStatus;
        this.errorName = errorName;
    }

    public int httpStatus() {
        return httpStatus;
    }

    public String errorName() {
        return errorName;
    }
}
