package com.koujunxin.member.api.port;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 账号绑定态命令端口。由 WP-10 / kjx-server 实现；本模块不得访问 account Mapper 或 kjx_account SQL。
 */
public interface AccountBindingCommandPort {

    Optional<AccountBindingView> find(long accountId);

    void updateBindStatus(long accountId, int bindStatus);

    /**
     * 绑定失败计数。{@code lockedUntil} 非空时写入 bind_locked_until。
     */
    void recordFailure(long accountId, int failCount, LocalDateTime lockedUntil);

    void resetFailures(long accountId);

    record AccountBindingView(
            long accountId,
            int accountStatus,
            int bindStatus,
            int bindFailCount,
            LocalDateTime bindLockedUntil
    ) {
        public static final int STATUS_NORMAL = 1;
        public static final int STATUS_CANCELLING = 3;
        public static final int BIND_NONE = 0;
        public static final int BIND_PENDING = 1;
        public static final int BIND_MATCHED = 2;
        public static final int BIND_REJECTED = 3;
        public static final int BIND_UNBINDING = 4;

        public boolean cancelling() {
            return accountStatus == STATUS_CANCELLING;
        }
    }
}
