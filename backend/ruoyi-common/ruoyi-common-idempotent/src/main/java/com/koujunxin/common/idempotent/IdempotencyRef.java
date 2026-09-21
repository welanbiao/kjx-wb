package com.koujunxin.common.idempotent;

/**
 * 幂等主键：scene + ref_id + idempotency_key。
 */
public record IdempotencyRef(String scene, long refId, String idempotencyKey) {

    public IdempotencyRef {
        if (scene == null || scene.isBlank()) {
            throw new IllegalArgumentException("scene is required");
        }
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("idempotencyKey is required");
        }
        scene = scene.trim();
        idempotencyKey = idempotencyKey.trim();
    }
}
