package com.koujunxin.ai.api;

public final class AiException extends RuntimeException {

    private final String name;
    private final int code;

    public AiException(String name, int code, String message) {
        super(message);
        this.name = name;
        this.code = code;
    }

    public static AiException byokFailed() {
        return new AiException("BYOK_FAILED", 1202, "上游调用失败，未扣平台余额");
    }

    public static AiException byokCredentialRequired() {
        return new AiException("CREDENTIAL_INVALID", 400, "密钥已失效，请重新配置");
    }

    public static AiException cancelled() {
        return new AiException("AI_CANCELLED", 499, "请求已取消");
    }

    public static AiException timeout() {
        return new AiException("AI_UPSTREAM_TIMEOUT", 504, "模型响应超时");
    }

    public static AiException promptMissing(String workCode) {
        return new AiException("PROMPT_NOT_FOUND", 404, "作品 Prompt 未配置");
    }

    public static AiException rejected(String message) {
        return new AiException("AI_REJECTED", 400, message);
    }

    public String name() {
        return name;
    }

    public int code() {
        return code;
    }
}
