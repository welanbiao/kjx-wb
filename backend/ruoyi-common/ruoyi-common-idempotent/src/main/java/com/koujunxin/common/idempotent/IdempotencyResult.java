package com.koujunxin.common.idempotent;

/**
 * 首次执行结果快照。重复请求且 request_hash 相同时原样返回。
 */
public record IdempotencyResult(String resultCode, String resultBodyJson) {

    public IdempotencyResult {
        if (resultCode == null || resultCode.isBlank()) {
            throw new IllegalArgumentException("resultCode is required");
        }
        resultCode = resultCode.trim();
        if (resultCode.length() > 32) {
            throw new IllegalArgumentException("resultCode exceeds 32 chars");
        }
        if (resultBodyJson == null || resultBodyJson.isBlank()) {
            resultBodyJson = "{}";
        }
    }

    public static IdempotencyResult of(String resultCode, String resultBodyJson) {
        return new IdempotencyResult(resultCode, resultBodyJson);
    }
}
