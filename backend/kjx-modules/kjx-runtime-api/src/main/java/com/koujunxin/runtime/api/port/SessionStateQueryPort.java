package com.koujunxin.runtime.api.port;

import com.koujunxin.runtime.api.dto.SessionRef;
import com.koujunxin.runtime.api.dto.SessionStateSnapshot;

/**
 * 会话状态只读端口。由 kjx-runtime 实现，kjx-tool 等模块只依赖本接口。
 */
public interface SessionStateQueryPort {

    SessionStateSnapshot load(SessionRef session);
}
