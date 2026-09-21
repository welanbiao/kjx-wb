package com.koujunxin.common.idempotent;

/**
 * 同键同参仍处于 PROCESSING（未提交结果）。调用方可稍后重试读取首次结果。
 */
public class IdempotencyInProgressException extends RuntimeException {

    private final IdempotencyRef ref;

    public IdempotencyInProgressException(IdempotencyRef ref) {
        super("idempotency key is still processing");
        this.ref = ref;
    }

    public IdempotencyRef ref() {
        return ref;
    }
}
