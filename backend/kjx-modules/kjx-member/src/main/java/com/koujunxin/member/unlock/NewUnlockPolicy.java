package com.koujunxin.member.unlock;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;

import com.koujunxin.member.api.MemberFailures;
import com.koujunxin.member.api.port.AccountBindingCommandPort.AccountBindingView;
import com.koujunxin.member.domain.MemberRecord;
import com.koujunxin.member.normalize.ExpireTimes;
import com.koujunxin.member.recalc.MemberRecalcService;
import com.koujunxin.work.api.WorkSnapshot;
import com.koujunxin.work.visibility.WorkVisibilityService;

/**
 * 仅用于新解锁。不检查已有 entitlement，不给 Tool/Runtime 复用。
 * 额度只用 subscribed_days / available_count，与绑定月数无关。
 */
public final class NewUnlockPolicy {

    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");

    private final MemberRecalcService recalc;
    private final WorkVisibilityService visibility;
    private final Clock clock;

    public NewUnlockPolicy(MemberRecalcService recalc, WorkVisibilityService visibility, Clock clock) {
        this.recalc = recalc;
        this.visibility = visibility;
        this.clock = clock == null ? Clock.system(SHANGHAI) : clock;
    }

    public void require(MemberRecord member, AccountBindingView account, WorkSnapshot work, long accountId) {
        if (account != null && account.cancelling()) {
            throw MemberFailures.cancelling();
        }
        if (member == null || !member.bound()
                || member.boundAccountId() == null
                || member.boundAccountId() != accountId) {
            throw MemberFailures.bindingRequired();
        }
        LocalDateTime now = LocalDateTime.now(clock.withZone(SHANGHAI));
        String status = recalc.status(member.startAt(), member.expireAt(), now);
        if ("NOT_STARTED".equals(status)) {
            throw MemberFailures.notStarted(ExpireTimes.format(member.startAt()));
        }
        if (!"VALID".equals(status)) {
            throw MemberFailures.expired();
        }
        if (member.unlockedCount() >= member.availableCount()) {
            throw MemberFailures.quotaExhausted();
        }
        if (work == null || work.deleted()) {
            throw MemberFailures.notFound();
        }
        if (!visibility.canNewUnlock(work, accountId)) {
            throw MemberFailures.unpublished();
        }
    }
}
