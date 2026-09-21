package com.koujunxin.work.packagecontent;

public class WorkPackageException extends RuntimeException {

    private final int httpStatus;
    private final String errorName;
    private final ValidationReport report;

    public WorkPackageException(int httpStatus, String errorName, String message) {
        this(httpStatus, errorName, message, null);
    }

    public WorkPackageException(int httpStatus, String errorName, String message, ValidationReport report) {
        super(message);
        this.httpStatus = httpStatus;
        this.errorName = errorName;
        this.report = report;
    }

    public static WorkPackageException badRequest(String errorName, String message) {
        return new WorkPackageException(400, errorName, message);
    }

    public static WorkPackageException notFound(String message) {
        return new WorkPackageException(404, "PACKAGE_NOT_FOUND", message);
    }

    public static WorkPackageException rejected(String errorName, String message, ValidationReport report) {
        return new WorkPackageException(400, errorName, message, report);
    }

    public int httpStatus() {
        return httpStatus;
    }

    public String errorName() {
        return errorName;
    }

    public ValidationReport report() {
        return report;
    }
}
