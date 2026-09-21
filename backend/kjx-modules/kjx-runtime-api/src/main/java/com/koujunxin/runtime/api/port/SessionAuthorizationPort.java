package com.koujunxin.runtime.api.port;

import com.koujunxin.runtime.api.dto.SessionRef;

/**
 * 会话归属校验端口。校验账号是否为会话所有者，不检查会员 VALID。
 */
public interface SessionAuthorizationPort {

    void requireOwner(long accountId, SessionRef session);
}
