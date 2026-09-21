package com.koujunxin.runtime.api.port;

import com.koujunxin.runtime.api.dto.ToolExecutionCommand;
import com.koujunxin.runtime.api.dto.ToolResult;

/**
 * 工具执行端口。由 kjx-tool 实现，kjx-runtime 只依赖本接口。
 */
public interface ToolExecutionPort {

    ToolResult execute(ToolExecutionCommand command);
}
