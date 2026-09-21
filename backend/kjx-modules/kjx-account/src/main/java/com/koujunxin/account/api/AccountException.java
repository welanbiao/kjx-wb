package com.koujunxin.account.api;

/**
 * 业务失败。HTTP 状态与 body.code 对齐 {@code contracts/errors.yaml}，本类不是第二份常量源。
 */
public class AccountException extends RuntimeException {

    private final int bizCode;
    private final int httpStatus;

    public AccountException(int bizCode, int httpStatus, String message) {
        super(message);
        this.bizCode = bizCode;
        this.httpStatus = httpStatus;
    }

    public int bizCode() {
        return bizCode;
    }

    public int httpStatus() {
        return httpStatus;
    }
}
