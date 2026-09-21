package com.koujunxin.member.unlock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.koujunxin.member.api.MemberException;
import com.koujunxin.member.api.port.AccountBindingCommandPort.AccountBindingView;
import com.koujunxin.member.domain.MemberRecord;
import com.koujunxin.member.recalc.MemberRecalcService;
import com.koujunxin.work.api.WorkSnapshot;
import com.koujunxin.work.catalog.AccessForm;
import com.koujunxin.work.catalog.WorkStatus;
import com.koujunxin.work.catalog.WorkType;
import com.koujunxin.work.visibility.WorkVisibilityService;

class NewUnlockPolicyTest {

    private final NewUnlockPolicy policy = new NewUnlockPolicy(
            new MemberRecalcService(),
            new WorkVisibilityService(),
            Clock.system(ZoneId.of("Asia/Shanghai")));

    @Test
    void claimMonthsDoesNotAppearOnPolicy() throws Exception {
        var methods = NewUnlockPolicy.class.getDeclaredMethods();
        for (var method : methods) {
            assertFalse(method.getName().toLowerCase().contains("claim"));
            assertFalse(method.toGenericString().toLowerCase().contains("claimmonths"));
            assertFalse(method.toGenericString().toLowerCase().contains("subscribedmonths"));
        }
        assertEquals(1, java.util.Arrays.stream(methods).filter(m -> m.getName().equals("require")).count());
    }

    @Test
    void expiredMemberCannotNewUnlock() {
        MemberException ex = assertThrows(MemberException.class,
                () -> policy.require(member("EXPIRED"), matchedAccount(), published(1), 9L));
        assertEquals(1006, ex.bizCode());
    }

    @Test
    void unpublishedWorkRejected() {
        MemberException ex = assertThrows(MemberException.class,
                () -> policy.require(member("VALID"), matchedAccount(), unpublished(2), 9L));
        assertEquals(1008, ex.bizCode());
    }

    @Test
    void unboundRejected() {
        MemberRecord unbound = member("VALID", 0, null);
        MemberException ex = assertThrows(MemberException.class,
                () -> policy.require(unbound, matchedAccount(), published(3), 9L));
        assertEquals(1009, ex.bizCode());
    }

    @Test
    void publishedValidBoundAllowed() {
        policy.require(member("VALID"), matchedAccount(), published(4), 9L);
    }

    private static MemberRecord member(String status) {
        return member(status, 1, 9L);
    }

    private static MemberRecord member(String status, int bindState, Long accountId) {
        LocalDateTime expire = "EXPIRED".equals(status)
                ? LocalDateTime.of(2020, 1, 1, 23, 59, 59, 999_000_000)
                : LocalDateTime.of(2027, 8, 9, 23, 59, 59, 999_000_000);
        LocalDateTime start = "EXPIRED".equals(status)
                ? expire.minusDays(300)
                : LocalDateTime.of(2026, 1, 1, 0, 0);
        return new MemberRecord(
                1L, "KJX-ABCD2345", "zhangsan", "zhangsan", 10L, 12, 300, expire, start,
                10, 0, 10, status, bindState, accountId, start, "", 0, start);
    }

    private static AccountBindingView matchedAccount() {
        return new AccountBindingView(9L, AccountBindingView.STATUS_NORMAL, AccountBindingView.BIND_MATCHED, 0, null);
    }

    private static WorkSnapshot published(long id) {
        return snapshot(id, WorkStatus.PUBLISHED, 0);
    }

    private static WorkSnapshot unpublished(long id) {
        return snapshot(id, WorkStatus.UNPUBLISHED, 0);
    }

    private static WorkSnapshot snapshot(long id, WorkStatus status, int gray) {
        return new WorkSnapshot(
                id, "work-" + id, "作品", WorkType.game, null, null, List.of(), null, List.of(),
                status, "1.0.0", "1.0.0", AccessForm.A, List.of(), gray, 0, Instant.now(), false);
    }
}
