package com.koujunxin.member.port;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import com.koujunxin.member.api.MemberFailures;
import com.koujunxin.member.api.port.AccountBindingCommandPort;

/**
 * 缺省实现：未装配 WP-10 适配器时拒绝写账号表，避免反向依赖与直连 Mapper。
 */
public final class MissingAccountBindingCommandPort implements AccountBindingCommandPort {

    private final ConcurrentHashMap<Long, AccountBindingView> local = new ConcurrentHashMap<>();

    public void put(AccountBindingView view) {
        local.put(view.accountId(), view);
    }

    @Override
    public Optional<AccountBindingView> find(long accountId) {
        return Optional.ofNullable(local.get(accountId));
    }

    @Override
    public void updateBindStatus(long accountId, int bindStatus) {
        AccountBindingView current = require(accountId);
        local.put(accountId, new AccountBindingView(
                accountId, current.accountStatus(), bindStatus, current.bindFailCount(), current.bindLockedUntil()));
    }

    @Override
    public void recordFailure(long accountId, int failCount, java.time.LocalDateTime lockedUntil) {
        AccountBindingView current = require(accountId);
        local.put(accountId, new AccountBindingView(
                accountId, current.accountStatus(), current.bindStatus(), failCount, lockedUntil));
    }

    @Override
    public void resetFailures(long accountId) {
        AccountBindingView current = require(accountId);
        local.put(accountId, new AccountBindingView(
                accountId, current.accountStatus(), current.bindStatus(), 0, null));
    }

    private AccountBindingView require(long accountId) {
        AccountBindingView view = local.get(accountId);
        if (view == null) {
            throw MemberFailures.notFound();
        }
        return view;
    }
}
