package com.koujunxin.runtime.api.port;

import com.koujunxin.runtime.api.dto.SessionRef;
import com.koujunxin.runtime.api.dto.SessionStateSnapshot;
import com.koujunxin.runtime.api.dto.StateMutation;

/**
 * 会话状态写入端口。由 kjx-runtime 实现，调用方不得直接操作 runtime Entity。
 */
public interface SessionStateCommandPort {

    SessionStateSnapshot apply(SessionRef session, StateMutation mutation);
}
