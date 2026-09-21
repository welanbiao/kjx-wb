package com.koujunxin.common.idempotent;

/**
 * begin 结果：首次占位、回放首次结果、或同哈希处理中。
 */
public sealed interface IdempotencyBegin permits IdempotencyBegin.FirstAttempt, IdempotencyBegin.Replay,
        IdempotencyBegin.InProgress {

    record FirstAttempt() implements IdempotencyBegin {
    }

    record Replay(IdempotencyResult result) implements IdempotencyBegin {
        public Replay {
            if (result == null) {
                throw new IllegalArgumentException("result is required");
            }
        }
    }

    record InProgress() implements IdempotencyBegin {
    }

    default boolean firstAttempt() {
        return this instanceof FirstAttempt;
    }

    default boolean replay() {
        return this instanceof Replay;
    }
}
