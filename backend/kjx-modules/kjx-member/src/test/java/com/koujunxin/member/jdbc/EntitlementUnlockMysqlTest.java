package com.koujunxin.member.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.koujunxin.member.api.MemberException;
import com.koujunxin.member.api.port.UnlockTransactionHook;
import com.koujunxin.member.application.MemberApplicationService.CreateHostCommand;
import com.koujunxin.member.application.MemberApplicationService.CreateMemberCommand;
import com.koujunxin.member.application.MemberApplicationService.SubmitBindingCommand;
import com.koujunxin.member.domain.HostRecord;
import com.koujunxin.member.domain.MemberRecord;
import com.koujunxin.member.entitlement.JdbcEntitlementAccessPort;
import com.koujunxin.member.entitlement.JdbcEntitlementQueryPort;
import com.koujunxin.member.normalize.ExpireTimes;
import com.koujunxin.member.support.FixedDeptAccessPort;
import com.koujunxin.member.support.MysqlMemberSupport;
import com.koujunxin.member.unlock.UnlockOutcome;
import com.koujunxin.runtime.api.error.EntitlementRequiredException;
import com.koujunxin.work.catalog.WorkStatus;
import com.koujunxin.work.jdbc.JdbcWorkRepository;
import com.koujunxin.work.query.PublicWorkQueryService;
import com.koujunxin.work.visibility.WorkVisibilityService;

class EntitlementUnlockMysqlTest extends MysqlMemberSupport {

    private static final LocalDateTime VALID_EXPIRE = ExpireTimes.parseRequired("2026-11-01T23:59:59.999+08:00");
    private static final LocalDateTime EXPIRED_AT = ExpireTimes.parseRequired("2020-01-01");

    private final FixedDeptAccessPort ops = FixedDeptAccessPort.ops(9);

    @BeforeEach
    void setUp() {
        truncateAll();
    }

    @Test
    void flywayCreatesEntitlementSegment() {
        Integer entitlement = jdbc().queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'kjx_member_entitlement'",
                Integer.class);
        Integer unlock = jdbc().queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'kjx_unlock_record'",
                Integer.class);
        assertEquals(1, entitlement);
        assertEquals(1, unlock);
        Integer versions = jdbc().queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version IN ('330','331')",
                Integer.class);
        assertEquals(2, versions);
        Integer memberWork = jdbc().queryForObject(
                """
                SELECT COUNT(*) FROM information_schema.statistics
                 WHERE table_schema = DATABASE() AND table_name = 'kjx_member_entitlement'
                   AND index_name = 'uk_kjx_entitlement_member_work'
                """,
                Integer.class);
        Integer accountWork = jdbc().queryForObject(
                """
                SELECT COUNT(*) FROM information_schema.statistics
                 WHERE table_schema = DATABASE() AND table_name = 'kjx_member_entitlement'
                   AND index_name = 'uk_kjx_entitlement_account_work'
                """,
                Integer.class);
        assertTrue(memberWork > 0);
        assertTrue(accountWork > 0);
    }

    @Test
    void concurrentQuotaTenExactlyTenSuccessAndUniqueFacts() throws Exception {
        Bound ctx = bindValid(300);
        List<String> codes = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            String code = "w" + i;
            works().insert(code, "作品" + i, WorkStatus.PUBLISHED, 0);
            codes.add(code);
        }
        ExecutorService pool = Executors.newFixedThreadPool(50);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger created = new AtomicInteger();
        AtomicInteger already = new AtomicInteger();
        AtomicInteger failed = new AtomicInteger();
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < 200; i++) {
                String code = codes.get(i);
                futures.add(pool.submit(() -> {
                    start.await();
                    try {
                        UnlockOutcome outcome = unlocks().unlock(ctx.accountId, code, key(), "r", "127.0.0.1", "dev");
                        if (outcome.code() == 0) {
                            created.incrementAndGet();
                        } else if (outcome.code() == 1010) {
                            already.incrementAndGet();
                        }
                    } catch (MemberException ex) {
                        failed.incrementAndGet();
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> future : futures) {
                future.get(2, TimeUnit.MINUTES);
            }
        } finally {
            pool.shutdownNow();
        }
        assertEquals(10, created.get());
        Integer entitlementCount = jdbc().queryForObject(
                "SELECT COUNT(*) FROM kjx_member_entitlement WHERE member_id = ?", Integer.class, ctx.memberId);
        Integer unlocked = jdbc().queryForObject(
                "SELECT unlocked_count FROM kjx_member WHERE id = ?", Integer.class, ctx.memberId);
        Integer successFacts = jdbc().queryForObject(
                """
                SELECT COUNT(*) FROM kjx_unlock_record
                 WHERE member_id = ? AND result = 'SUCCESS' AND source = 'user'
                """,
                Integer.class, ctx.memberId);
        assertEquals(10, entitlementCount);
        assertEquals(10, unlocked);
        assertEquals(10, successFacts);
        assertEquals(entitlementCount, unlocked);
        Integer uniquePairs = jdbc().queryForObject(
                "SELECT COUNT(DISTINCT CONCAT(member_id, ':', work_id)) FROM kjx_member_entitlement",
                Integer.class);
        assertEquals(entitlementCount, uniquePairs);
        assertTrue(failed.get() >= 190);
    }

    @Test
    void sameWorkConcurrentOnlyOneSuccessFactAndDuplicateKeyRollsBack() throws Exception {
        Bound ctx = bindValid(300);
        works().insert("same-work", "同一作品", WorkStatus.PUBLISHED, 0);
        ExecutorService pool = Executors.newFixedThreadPool(20);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger created = new AtomicInteger();
        AtomicInteger already = new AtomicInteger();
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < 20; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    try {
                        UnlockOutcome outcome = unlocks().unlock(ctx.accountId, "same-work", key(), "r", "127.0.0.1", "d");
                        if (outcome.code() == 0) {
                            created.incrementAndGet();
                        } else {
                            already.incrementAndGet();
                        }
                    } catch (DuplicateKeyException ex) {
                        already.incrementAndGet();
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> future : futures) {
                future.get(1, TimeUnit.MINUTES);
            }
        } finally {
            pool.shutdownNow();
        }
        assertEquals(1, entitlements().countByMemberId(ctx.memberId));
        assertEquals(1, jdbc().queryForObject(
                "SELECT unlocked_count FROM kjx_member WHERE id = ?", Integer.class, ctx.memberId));
        assertEquals(1, created.get());
    }

    @Test
    void idempotentReplayAndConflict() {
        Bound ctx = bindValid(300);
        works().insert("idem", "幂等", WorkStatus.PUBLISHED, 0);
        String idem = key();
        UnlockOutcome first = unlocks().unlock(ctx.accountId, "idem", idem, "r", "127.0.0.1", "dev-a");
        assertEquals(0, first.code());
        UnlockOutcome replay = unlocks().unlock(ctx.accountId, "idem", idem, "r", "127.0.0.1", "dev-a");
        assertEquals(1010, replay.code());
        assertEquals(true, replay.data().get("alreadyUnlocked"));
        assertEquals(1, entitlements().countByMemberId(ctx.memberId));

        long other = accounts().insertAccount();
        MemberException conflict = assertThrows(MemberException.class,
                () -> unlocks().unlock(other, "idem", idem, "r", "127.0.0.1", "dev-a"));
        assertEquals(1090, conflict.bizCode());
        assertEquals(409, conflict.httpStatus());
    }

    @Test
    void existingEntitlementShortCircuitsWithoutQuota() {
        Bound ctx = bindValid(30);
        works().insert("owned", "已有", WorkStatus.PUBLISHED, 0);
        unlocks().adjust(ops, ctx.memberId, "ADD", "owned", "补偿", key(), "r");
        MemberRecord before = service().getMember(ops, ctx.memberId);
        UnlockOutcome outcome = unlocks().unlock(ctx.accountId, "owned", key(), "r", "127.0.0.1", "d");
        assertEquals(1010, outcome.code());
        MemberRecord after = service().getMember(ops, ctx.memberId);
        assertEquals(before.unlockedCount(), after.unlockedCount());
        assertEquals(1, entitlements().countByMemberId(ctx.memberId));
    }

    @Test
    void expiredWithEntitlementAllowedWithoutDenied() {
        Bound ctx = bindExpiredWithEntitlement();
        JdbcEntitlementAccessPort access = new JdbcEntitlementAccessPort(entitlements(), works(), audit());
        access.requireEntitlement(ctx.accountId, "kept");
        UnlockOutcome outcome = unlocks().unlock(ctx.accountId, "kept", key(), "r", "127.0.0.1", "d");
        assertEquals(1010, outcome.code());
        assertEquals(true, outcome.data().get("alreadyUnlocked"));
    }

    @Test
    void expiredWithoutEntitlementCannotUnlockOrAccess() {
        Bound ctx = bindExpired();
        works().insert("fresh", "新解锁", WorkStatus.PUBLISHED, 0);
        MemberException unlock = assertThrows(MemberException.class,
                () -> unlocks().unlock(ctx.accountId, "fresh", key(), "r", "127.0.0.1", "d"));
        assertEquals(1006, unlock.bizCode());
        JdbcEntitlementAccessPort access = new JdbcEntitlementAccessPort(entitlements(), works(), audit());
        assertThrows(EntitlementRequiredException.class, () -> access.requireEntitlement(ctx.accountId, "fresh"));
    }

    @Test
    void unpublishedExistingAllowedNewUnlockRejectedAndQueryPortShortCircuit() {
        Bound ctx = bindValid(300);
        long workId = works().insert("down", "下架", WorkStatus.PUBLISHED, 0);
        UnlockOutcome created = unlocks().unlock(ctx.accountId, "down", key(), "r", "127.0.0.1", "d");
        assertEquals(0, created.code());
        works().setStatus(workId, WorkStatus.UNPUBLISHED);
        UnlockOutcome again = unlocks().unlock(ctx.accountId, "down", key(), "r", "127.0.0.1", "d");
        assertEquals(1010, again.code());
        works().insert("down-new", "新下架", WorkStatus.UNPUBLISHED, 0);
        MemberException rejected = assertThrows(MemberException.class,
                () -> unlocks().unlock(ctx.accountId, "down-new", key(), "r", "127.0.0.1", "d"));
        assertEquals(1008, rejected.bizCode());

        PublicWorkQueryService query = new PublicWorkQueryService(
                new JdbcWorkRepository(jdbc(), new ObjectMapper()),
                new WorkVisibilityService(),
                new JdbcEntitlementQueryPort(entitlements()));
        assertTrue(query.getDetail(ctx.accountId, "down").unlocked());
        assertTrue(query.canAccessUnpublishedIfEntitled(ctx.accountId, "down"));
        assertFalse(query.canAccessUnpublishedIfEntitled(ctx.accountId, "down-new"));
    }

    @Test
    void grayBucketStableAndMatchesVisibilityService() {
        Bound included = bindValid(300);
        Bound other = bindValidOtherHost(300);
        long workId = works().insert("gray-one", "灰度", WorkStatus.GRAY, 50);
        var snapshot = works().findById(workId).orElseThrow();
        WorkVisibilityService visibility = new WorkVisibilityService();
        boolean first = visibility.canNewUnlock(snapshot, included.accountId);
        boolean second = visibility.canNewUnlock(snapshot, other.accountId);
        int expected = (first ? 1 : 0) + (second ? 1 : 0);
        int actual = 0;
        try {
            UnlockOutcome a = unlocks().unlock(included.accountId, "gray-one", key(), "r", "127.0.0.1", "d");
            if (a.code() == 0) {
                actual++;
            }
        } catch (MemberException ex) {
            assertEquals(1008, ex.bizCode());
            assertFalse(first);
        }
        try {
            UnlockOutcome b = unlocks().unlock(other.accountId, "gray-one", key(), "r", "127.0.0.1", "d");
            if (b.code() == 0) {
                actual++;
            }
        } catch (MemberException ex) {
            assertEquals(1008, ex.bizCode());
            assertFalse(second);
        }
        assertEquals(expected, actual);
        assertEquals(first, visibility.canNewUnlock(snapshot, included.accountId));
    }

    @Test
    void faultInjectionRollsBackCasWithoutDrift() {
        Bound ctx = bindValid(300);
        works().insert("fault", "故障", WorkStatus.PUBLISHED, 0);
        useUnlockHook(new UnlockTransactionHook() {
            @Override
            public void afterAudit() {
                throw new IllegalStateException("audit fault");
            }
        });
        assertThrows(IllegalStateException.class,
                () -> unlocks().unlock(ctx.accountId, "fault", key(), "r", "127.0.0.1", "d"));
        assertNoDrift(ctx.memberId);

        useUnlockHook(new UnlockTransactionHook() {
            @Override
            public void afterOutbox() {
                throw new IllegalStateException("outbox fault");
            }
        });
        assertThrows(IllegalStateException.class,
                () -> unlocks().unlock(ctx.accountId, "fault", key(), "r", "127.0.0.1", "d"));
        assertNoDrift(ctx.memberId);

        useUnlockHook(new UnlockTransactionHook() {
            @Override
            public void beforeEntitlementInsert() {
                throw new DuplicateKeyException("injected duplicate");
            }
        });
        assertThrows(DuplicateKeyException.class,
                () -> unlocks().unlock(ctx.accountId, "fault", key(), "r", "127.0.0.1", "d"));
        assertNoDrift(ctx.memberId);
    }

    @Test
    void officialAddRemoveRecalcAndDeptIsolation() {
        Bound a = bindValid(300);
        Bound b = bindValidOtherHost(300);
        works().insert("gift", "赠送", WorkStatus.PUBLISHED, 0);
        MemberRecord added = unlocks().adjust(ops, a.memberId, "ADD", "gift", "客服补发", key(), "r");
        assertEquals(1, added.unlockedCount());
        assertEquals(1, entitlements().countByMemberId(a.memberId));
        MemberRecord recalc = service().recalcMember(ops, a.memberId, "对账", key());
        assertEquals(1, recalc.unlockedCount());
        MemberRecord removed = unlocks().adjust(ops, a.memberId, "REMOVE", "gift", "误加撤回", key(), "r");
        assertEquals(0, removed.unlockedCount());
        assertEquals(0, entitlements().countByMemberId(a.memberId));

        FixedDeptAccessPort limited = FixedDeptAccessPort.opsLimited(88, a.deptId);
        assertEquals(403, biz(() -> unlocks().pageRecords(limited, b.deptId, b.memberId, null, null, null, 1, 20)));
        assertEquals(403, biz(() -> unlocks().adjust(limited, b.memberId, "ADD", "gift", "越权", key(), "r")));
        FixedDeptAccessPort hostA = FixedDeptAccessPort.host(21, a.deptId);
        assertEquals(403, biz(() -> unlocks().adjust(hostA, a.memberId, "ADD", "gift", "主播", key(), "r")));
        assertEquals(403, biz(() -> unlocks().pageRecords(hostA, b.deptId, b.memberId, null, null, null, 1, 20)));
        assertEquals(403, biz(() -> unlocks().exportRecords(hostA, a.deptId, a.memberId, null, null)));
        assertNotNull(unlocks().pageRecords(hostA, null, a.memberId, null, null, null, 1, 20));
        byte[] exported = com.koujunxin.member.web.UnlockXlsxExporter.export(
                unlocks().exportRecords(ops, a.deptId, a.memberId, null, null));
        assertTrue(exported.length > 0);
    }

    @Test
    void failRecordsAreWindowMerged() {
        Bound ctx = bindValid(300);
        works().insert("hidden", "下架", WorkStatus.UNPUBLISHED, 0);
        for (int i = 0; i < 30; i++) {
            MemberException ex = assertThrows(MemberException.class,
                    () -> unlocks().unlock(ctx.accountId, "hidden", key(), "r", "127.0.0.1", "d"));
            assertEquals(1008, ex.bizCode());
        }
        Integer failed = jdbc().queryForObject(
                "SELECT COUNT(*) FROM kjx_unlock_record WHERE result = 'FAILED' AND work_id = (SELECT id FROM kjx_work WHERE work_code = 'hidden')",
                Integer.class);
        assertEquals(1, failed);
        assertTrue(failed < 30);
    }

    @Test
    void entitlementPortsCoverStatusMatrix() {
        Bound valid = bindValid(300);
        works().insert("port", "端口", WorkStatus.PUBLISHED, 0);
        JdbcEntitlementQueryPort query = new JdbcEntitlementQueryPort(entitlements());
        JdbcEntitlementAccessPort access = new JdbcEntitlementAccessPort(entitlements(), works(), audit());
        assertFalse(query.hasEntitlement(valid.accountId, works().findByCode("port").orElseThrow().id()));
        assertThrows(EntitlementRequiredException.class, () -> access.requireEntitlement(valid.accountId, "port"));
        unlocks().unlock(valid.accountId, "port", key(), "r", "127.0.0.1", "d");
        assertTrue(query.hasEntitlement(valid.accountId, works().findByCode("port").orElseThrow().id()));
        access.requireEntitlement(valid.accountId, "port");

        Bound expired = bindExpiredWithEntitlement();
        access.requireEntitlement(expired.accountId, "kept");
        assertTrue(query.hasEntitlement(expired.accountId, works().findByCode("kept").orElseThrow().id()));
    }

    private void assertNoDrift(long memberId) {
        Integer entitlementsCount = jdbc().queryForObject(
                "SELECT COUNT(*) FROM kjx_member_entitlement WHERE member_id = ?", Integer.class, memberId);
        Integer unlocked = jdbc().queryForObject(
                "SELECT unlocked_count FROM kjx_member WHERE id = ?", Integer.class, memberId);
        Integer outbox = jdbc().queryForObject(
                "SELECT COUNT(*) FROM kjx_outbox_event WHERE aggregate_type IN ('entitlement','unlock')",
                Integer.class);
        assertEquals(0, entitlementsCount);
        assertEquals(0, unlocked);
        assertEquals(0, outbox);
    }

    private Bound bindValid(int days) {
        HostRecord host = createHost("KJX", "叩君芯");
        MemberRecord member = service().createMember(FixedDeptAccessPort.host(5, host.deptId()), host.deptId(),
                new CreateMemberCommand("zhangsan" + key().substring(0, 6), 12, days, VALID_EXPIRE, "", null),
                key(), false);
        long accountId = accounts().insertAccount();
        service().submitBinding(accountId,
                new SubmitBindingCommand(host.hostCode(), member.douyinId(), member.memberCode(), 12),
                key(), "r", "127.0.0.1", "d");
        long bindingId = jdbc().queryForObject(
                "SELECT id FROM kjx_member_binding WHERE account_id = ?", Long.class, accountId);
        service().approveBinding(ops, bindingId, "ok", key(), "r");
        return new Bound(host.deptId(), member.id(), accountId);
    }

    private Bound bindValidOtherHost(int days) {
        HostRecord host = createHost("BBB", "主播B");
        MemberRecord member = service().createMember(FixedDeptAccessPort.host(6, host.deptId()), host.deptId(),
                new CreateMemberCommand("lisi" + key().substring(0, 6), 12, days, VALID_EXPIRE, "", null),
                key(), false);
        long accountId = accounts().insertAccount();
        service().submitBinding(accountId,
                new SubmitBindingCommand(host.hostCode(), member.douyinId(), member.memberCode(), 12),
                key(), "r", "127.0.0.1", "d");
        long bindingId = jdbc().queryForObject(
                "SELECT id FROM kjx_member_binding WHERE account_id = ?", Long.class, accountId);
        service().approveBinding(ops, bindingId, "ok", key(), "r");
        return new Bound(host.deptId(), member.id(), accountId);
    }

    private Bound bindExpired() {
        HostRecord host = createHost("EXP", "过期主播");
        MemberRecord member = service().createMember(FixedDeptAccessPort.host(7, host.deptId()), host.deptId(),
                new CreateMemberCommand("old" + key().substring(0, 6), 12, 300, EXPIRED_AT, "", null),
                key(), false);
        long accountId = accounts().insertAccount();
        service().submitBinding(accountId,
                new SubmitBindingCommand(host.hostCode(), member.douyinId(), member.memberCode(), 12),
                key(), "r", "127.0.0.1", "d");
        long bindingId = jdbc().queryForObject(
                "SELECT id FROM kjx_member_binding WHERE account_id = ?", Long.class, accountId);
        service().approveBinding(ops, bindingId, "ok", key(), "r");
        return new Bound(host.deptId(), member.id(), accountId);
    }

    private Bound bindExpiredWithEntitlement() {
        Bound ctx = bindExpired();
        works().insert("kept", "保留", WorkStatus.PUBLISHED, 0);
        unlocks().adjust(ops, ctx.memberId, "ADD", "kept", "过期保留", key(), "r");
        return ctx;
    }

    private HostRecord createHost(String code, String name) {
        return service().createHost(ops, new CreateHostCommand(code, name, null, 101L, null, null, null, null, "建主播"),
                key(), "req");
    }

    private static String key() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    private static int biz(Runnable action) {
        try {
            action.run();
            return 0;
        } catch (MemberException ex) {
            return ex.bizCode();
        }
    }

    private record Bound(long deptId, long memberId, long accountId) {
    }
}
