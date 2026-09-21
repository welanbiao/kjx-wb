package com.koujunxin.work.packagecontent;

public record ReviewFinding(ReviewCheck check, Status status, String message, String locator) {

    public enum Status {
        PASS,
        FAIL,
        WARN
    }

    public static ReviewFinding pass(ReviewCheck check, String message) {
        return new ReviewFinding(check, Status.PASS, message, null);
    }

    public static ReviewFinding fail(ReviewCheck check, String message, String locator) {
        return new ReviewFinding(check, Status.FAIL, message, locator);
    }

    public static ReviewFinding warn(ReviewCheck check, String message, String locator) {
        return new ReviewFinding(check, Status.WARN, message, locator);
    }

    public boolean failed() {
        return status == Status.FAIL;
    }
}
