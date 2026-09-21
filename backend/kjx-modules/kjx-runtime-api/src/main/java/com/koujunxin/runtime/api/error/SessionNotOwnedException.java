package com.koujunxin.runtime.api.error;

/**
 * 会话不属于当前账号。不表达会员 VALID 状态。
 */
public class SessionNotOwnedException extends RuntimePortException {

    public SessionNotOwnedException(long accountId, String sessionId) {
        super("SESSION_NOT_OWNED", "account " + accountId + " is not owner of session");
        this.accountId = accountId;
        this.sessionId = sessionId;
    }

    private final long accountId;
    private final String sessionId;

    public long accountId() {
        return accountId;
    }

    public String sessionId() {
        return sessionId;
    }
}
