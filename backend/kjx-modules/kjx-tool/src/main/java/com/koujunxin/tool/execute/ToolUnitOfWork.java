package com.koujunxin.tool.execute;

import java.util.function.Supplier;

public interface ToolUnitOfWork {

    <T> T inTransaction(Supplier<T> work);
}
