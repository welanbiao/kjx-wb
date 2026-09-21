package com.koujunxin.common.idempotent;

import java.time.Duration;

/**
 * 幂等场景命令。requestHash 必须是 CanonicalRequestHash 生成的 64 位十六进制 SHA-256。
 */
public record IdempotencyCommand(
        String scene,
        long refId,
        String idempotencyKey,
        Long actorId,
        String requestHash,
        Duration ttl
) {

    public static final int KEY_MAX_LENGTH = 64;
    public static final int HASH_LENGTH = 64;
    public static final Duration DEFAULT_TTL = Duration.ofHours(24);

    public IdempotencyCommand {
        scene = requireText(scene, "scene");
        if (scene.length() > 32) {
            throw new IllegalArgumentException("scene exceeds 32 chars");
        }
        if (refId < 0) {
            throw new IllegalArgumentException("refId must be >= 0");
        }
        idempotencyKey = requireText(idempotencyKey, "idempotencyKey");
        if (idempotencyKey.length() > KEY_MAX_LENGTH) {
            throw new IllegalArgumentException("idempotencyKey exceeds 64 chars");
        }
        requestHash = requireText(requestHash, "requestHash").toLowerCase();
        if (requestHash.length() != HASH_LENGTH || !requestHash.chars().allMatch(c ->
                (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f'))) {
            throw new IllegalArgumentException("requestHash must be 64-char sha-256 hex");
        }
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            ttl = DEFAULT_TTL;
        }
    }

    public IdempotencyRef ref() {
        return new IdempotencyRef(scene, refId, idempotencyKey);
    }

    public static IdempotencyCommand of(String scene, long refId, String key, Object payload) {
        return new IdempotencyCommand(scene, refId, key, null, CanonicalRequestHash.sha256Hex(payload), DEFAULT_TTL);
    }

    public IdempotencyCommand withActor(Long actor) {
        return new IdempotencyCommand(scene, refId, idempotencyKey, actor, requestHash, ttl);
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.trim();
    }
}
