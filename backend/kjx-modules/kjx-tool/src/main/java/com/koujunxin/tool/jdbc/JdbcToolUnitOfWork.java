package com.koujunxin.tool.jdbc;

import java.util.function.Supplier;

import org.springframework.transaction.support.TransactionTemplate;

import com.koujunxin.tool.execute.ToolUnitOfWork;

public final class JdbcToolUnitOfWork implements ToolUnitOfWork {

    private final TransactionTemplate tx;

    public JdbcToolUnitOfWork(TransactionTemplate tx) {
        this.tx = tx;
    }

    @Override
    public <T> T inTransaction(Supplier<T> work) {
        return tx.execute(status -> work.get());
    }
}
