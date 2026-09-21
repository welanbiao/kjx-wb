package com.koujunxin.account.api.port;

import java.util.Optional;

/**
 * 已绑定会员快照。由 WP-11 实现；本模块默认返回 empty，登录 {@code member} 为 null。
 */
@FunctionalInterface
public interface BoundMemberQueryPort {

    Optional<Object> findSnapshot(long accountId);
}
