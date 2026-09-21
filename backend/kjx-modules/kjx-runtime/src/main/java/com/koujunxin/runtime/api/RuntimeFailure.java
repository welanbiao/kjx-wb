package com.koujunxin.runtime.api;

/**
 * 运行时业务失败。errorName 记入 INTEGRATION，未改 contracts/errors.yaml。
 */
public final class RuntimeFailure extends RuntimeException {

    private final String errorName;
    private final int httpStatus;

    public RuntimeFailure(String errorName, int httpStatus, String message) {
        super(message);
        this.errorName = errorName == null || errorName.isBlank() ? "RUNTIME_ERROR" : errorName;
        this.httpStatus = httpStatus;
    }

    public String errorName() {
        return errorName;
    }

    public int httpStatus() {
        return httpStatus;
    }

    public static RuntimeFailure badRequest(String name, String message) {
        return new RuntimeFailure(name, 400, message);
    }

    public static RuntimeFailure forbidden(String name, String message) {
        return new RuntimeFailure(name, 403, message);
    }

    public static RuntimeFailure notFound(String name, String message) {
        return new RuntimeFailure(name, 404, message);
    }

    public static RuntimeFailure conflict(String message) {
        return new RuntimeFailure("SAVE_CONFLICT", 409, message);
    }

    public static RuntimeFailure schema(String message) {
        return new RuntimeFailure("SCHEMA_MISMATCH", 400, message);
    }

    public static RuntimeFailure checksum(String message) {
        return new RuntimeFailure("CHECKSUM_MISMATCH", 400, message);
    }

    public static RuntimeFailure crossWork() {
        return forbidden("CROSS_WORK", "session workCode mismatch");
    }

    public static RuntimeFailure crossSession() {
        return forbidden("CROSS_SESSION", "session not found for this request");
    }

    public static RuntimeFailure sessionEnded() {
        return forbidden("SESSION_ENDED", "session has ended");
    }
}
