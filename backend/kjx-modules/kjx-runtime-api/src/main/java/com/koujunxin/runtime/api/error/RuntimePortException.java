package com.koujunxin.runtime.api.error;

/**
 * runtime-api 端口失败基类。实现模块应抛出本类型或其子类，不得泄漏 Entity。
 */
public class RuntimePortException extends RuntimeException {

    private final String code;

    public RuntimePortException(String code, String message) {
        super(message);
        this.code = code == null || code.isBlank() ? "RUNTIME_PORT_ERROR" : code;
    }

    public String code() {
        return code;
    }
}
