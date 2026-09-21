package com.koujunxin.common.idempotent;

import java.util.function.Supplier;

/**
 * 幂等守护服务。必须在当前 Spring 事务内调用：INSERT 占位与业务写入同提交同回滚。
 */
public interface IdempotencyService {

    IdempotencyBegin begin(IdempotencyCommand command);

    void completeSuccess(IdempotencyRef ref, IdempotencyResult result);

    void completeFailure(IdempotencyRef ref, IdempotencyResult result);

    /**
     * 首次执行 action 并写入结果；同键同参直接返回首次结果；同键异参抛 {@link IdempotencyConflictException}。
     */
    default IdempotencyResult execute(IdempotencyCommand command, Supplier<IdempotencyResult> action) {
        IdempotencyBegin begin = begin(command);
        if (begin instanceof IdempotencyBegin.Replay replay) {
            return replay.result();
        }
        if (begin instanceof IdempotencyBegin.InProgress) {
            throw new IdempotencyInProgressException(command.ref());
        }
        try {
            IdempotencyResult result = action.get();
            completeSuccess(command.ref(), result);
            return result;
        } catch (RuntimeException | Error ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("idempotent action failed", ex);
        }
    }
}
