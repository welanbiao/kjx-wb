package com.koujunxin.common.idempotent;

/**
 * 相同 scene/ref_id/idempotency_key 但 request_hash 不同。
 * 对应 HTTP 409 / 业务码 1090 IDEMPOTENCY_CONFLICT（contracts/errors.yaml）。
 */
public class IdempotencyConflictException extends RuntimeException {

    public static final String NAME = "IDEMPOTENCY_CONFLICT";
    public static final int BUSINESS_CODE = 1090;
    public static final int HTTP_STATUS = 409;

    private final IdempotencyRef ref;

    public IdempotencyConflictException(IdempotencyRef ref) {
        super("idempotency conflict: same key with different request hash");
        this.ref = ref;
    }

    public IdempotencyRef ref() {
        return ref;
    }

    public String businessCodeName() {
        return NAME;
    }

    public int businessCode() {
        return BUSINESS_CODE;
    }

    public int httpStatus() {
        return HTTP_STATUS;
    }
}
