package com.koujunxin.member.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.koujunxin.member.api.MemberException;
import com.koujunxin.member.api.port.AccountBindingCommandPort.AccountBindingView;
import com.koujunxin.member.api.port.ReviewTransactionHook;
import com.koujunxin.member.application.MemberApplicationService;
import com.koujunxin.member.application.MemberApplicationService.CreateHostCommand;
import com.koujunxin.member.application.MemberApplicationService.CreateMemberCommand;
import com.koujunxin.member.application.MemberApplicationService.SubmitBindingCommand;
import com.koujunxin.member.application.MemberApplicationService.UpdateMemberCommand;
import com.koujunxin.member.domain.BindingRecord;
import com.koujunxin.member.domain.HostRecord;
import com.koujunxin.member.domain.MemberRecord;
import com.koujunxin.member.normalize.ExpireTimes;
import com.koujunxin.member.normalize.MemberCodes;
import com.koujunxin.member.support.FixedDeptAccessPort;
import com.koujunxin.member.support.MysqlMemberSupport;

class MemberFlowMysqlTest extends MysqlMemberSupport {

    private static final LocalDateTime EXPIRE = ExpireTimes.parseRequired("2027-08-09T23:59:59.999+08:00");

    private final FixedDeptAccessPort ops = FixedDeptAccessPort.ops(9);

    @BeforeEach
    void setUp() {
        truncateAll();
    }

    @Test
    void flywayCreatesMemberSegmentOnStandardClasspath() {
        Integer hosts = jdbc().queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'kjx_host'",
                Integer.class);
        Integer members = jdbc().queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'kjx_member'",
                Integer.class);
        Integer bindings = jdbc().queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'kjx_member_binding'",
                Integer.class);
        Integer appeals = jdbc().queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'kjx_binding_appeal'",
                Integer.class);
        assertEquals(1, hosts);
        assertEquals(1, members);
        assertEquals(1, bindings);
        assertEquals(1, appeals);
        Integer v300 = jdbc().queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version IN ('300','301','302','303')",
                Integer.class);
        assertEquals(4, v300);
    }

    @Test
    void hostCreateMemberFifteenMonths435DaysAndNoMonthDayCoupling() {
        HostRecord host = createHost("KJX", "叩君芯");
        FixedDeptAccessPort hostAccess = FixedDeptAccessPort.host(21, host.deptId());
        MemberRecord created = service().createMember(hostAccess, host.deptId(),
                new CreateMemberCommand("ZhangSan", 15, 435, EXPIRE, "", null), key(), false);
        assertEquals(14, created.availableCount());
        assertEquals(15, created.subscribedMonths());
        assertEquals(435, created.subscribedDays());
        assertTrue(created.memberCode().startsWith("KJX-"));
        for (char ch : created.memberCode().substring(4).toCharArray()) {
            assertFalse(MemberCodes.forbiddenChar(ch));
        }

        MemberRecord afterMonths = service().updateMember(hostAccess, created.id(),
                new UpdateMemberCommand(16, null, null, null, "改月数"), key());
        assertEquals(16, afterMonths.subscribedMonths());
        assertEquals(435, afterMonths.subscribedDays());
        assertEquals(14, afterMonths.availableCount());

        MemberRecord afterDays = service().updateMember(hostAccess, created.id(),
                new UpdateMemberCommand(null, 465, null, null, "改天数"), key());
        assertEquals(16, afterDays.subscribedMonths());
        assertEquals(465, afterDays.subscribedDays());
        assertEquals(15, afterDays.availableCount());
        assertEquals(2, audit().entries().stream().filter(e -> "UPDATE".equals(e.action())).count());
    }

    @Test
    void concurrentMemberCodesAreUnique() throws Exception {
        HostRecord host = createHost("ABC", "主播A");
        FixedDeptAccessPort hostAccess = FixedDeptAccessPort.host(3, host.deptId());
        int n = 20;
        ExecutorService pool = Executors.newFixedThreadPool(n);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger ok = new AtomicInteger();
        try {
            var futures = new java.util.ArrayList<Future<String>>();
            for (int i = 0; i < n; i++) {
                int idx = i;
                futures.add(pool.submit(() -> {
                    start.await();
                    MemberRecord created = service().createMember(hostAccess, host.deptId(),
                            new CreateMemberCommand("user" + idx, 12, 360, EXPIRE, "", null), key() + idx, false);
                    ok.incrementAndGet();
                    return created.memberCode();
                }));
            }
            start.countDown();
            Set<String> codes = new java.util.HashSet<>();
            for (Future<String> future : futures) {
                codes.add(future.get(30, TimeUnit.SECONDS));
            }
            assertEquals(n, codes.size());
            assertEquals(n, ok.get());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void tripleMatchErrorsAreUniformAndThreeFailsLock() {
        HostRecord host = createHost("KJX", "叩君芯");
        FixedDeptAccessPort hostAccess = FixedDeptAccessPort.host(4, host.deptId());
        MemberRecord member = service().createMember(hostAccess, host.deptId(),
                new CreateMemberCommand("zhangsan", 15, 435, EXPIRE, "", null), key(), false);
        long accountId = accounts().insertAccount();

        String wrongCode = member.memberCode().endsWith("2")
                ? member.memberCode().substring(0, member.memberCode().length() - 1) + "3"
                : member.memberCode().substring(0, member.memberCode().length() - 1) + "2";
        assertNotEquals(member.memberCode(), wrongCode);
        assertEquals(1001, biz(() -> service().submitBinding(accountId,
                new SubmitBindingCommand("KJX", "wrong", member.memberCode(), 15), key(), "r", "127.0.0.1", "dev")));
        assertEquals(1001, biz(() -> service().submitBinding(accountId,
                new SubmitBindingCommand("KJX", "zhangsan", member.memberCode(), 14), key(), "r", "127.0.0.1", "dev")));
        assertEquals(1004, biz(() -> service().submitBinding(accountId,
                new SubmitBindingCommand("KJX", "zhangsan", wrongCode, 15), key(), "r", "127.0.0.1", "dev")));
        assertEquals(1004, biz(() -> service().submitBinding(accountId,
                new SubmitBindingCommand("KJX", "zhangsan", member.memberCode(), 15), key(), "r", "127.0.0.1", "dev")));
        Integer fail = jdbc().queryForObject("SELECT bind_fail_count FROM kjx_account WHERE id = ?", Integer.class,
                accountId);
        assertTrue(fail >= 3);
    }

    @Test
    void pendingReviewApproveOutboxAndBoundMemberQueryPort() {
        HostRecord host = createHost("KJX", "叩君芯");
        FixedDeptAccessPort hostAccess = FixedDeptAccessPort.host(5, host.deptId());
        MemberRecord member = service().createMember(hostAccess, host.deptId(),
                new CreateMemberCommand("zhangsan", 15, 435, EXPIRE, "", null), key(), false);
        long accountId = accounts().insertAccount();
        Map<String, Object> submitted = service().submitBinding(accountId,
                new SubmitBindingCommand("KJX", "@ZhangSan", member.memberCode().toLowerCase(), 15),
                key(), "req", "127.0.0.1", "dev");
        assertEquals(BindingRecord.PENDING_REVIEW, submitted.get("status"));
        assertEquals(AccountBindingView.BIND_PENDING,
                accounts().find(accountId).orElseThrow().bindStatus());

        long bindingId = jdbc().queryForObject(
                "SELECT id FROM kjx_member_binding WHERE account_id = ?", Long.class, accountId);
        service().approveBinding(ops, bindingId, "ok", key(), "req");
        assertEquals(AccountBindingView.BIND_MATCHED, accounts().find(accountId).orElseThrow().bindStatus());
        assertTrue(snapshots().findSnapshot(accountId).isPresent());
        Integer outbox = jdbc().queryForObject(
                "SELECT COUNT(*) FROM kjx_outbox_event WHERE aggregate_type IN ('binding','notification')",
                Integer.class);
        assertTrue(outbox >= 2);
        assertEquals("MATCHED", service().getBinding(accountId).get("status"));
    }

    @Test
    void concurrentApproveOnlyOneSucceeds() throws Exception {
        HostRecord host = createHost("KJX", "叩君芯");
        MemberRecord member = service().createMember(FixedDeptAccessPort.host(6, host.deptId()), host.deptId(),
                new CreateMemberCommand("zhangsan", 15, 435, EXPIRE, "", null), key(), false);
        long accountId = accounts().insertAccount();
        service().submitBinding(accountId,
                new SubmitBindingCommand("KJX", "zhangsan", member.memberCode(), 15), key(), "r", null, "d");
        long bindingId = jdbc().queryForObject(
                "SELECT id FROM kjx_member_binding WHERE account_id = ?", Long.class, accountId);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger conflict = new AtomicInteger();
        try {
            Runnable task = () -> {
                try {
                    start.await();
                    service().approveBinding(ops, bindingId, "ok", UUID.randomUUID().toString().replace("-", ""), "r");
                    success.incrementAndGet();
                } catch (MemberException ex) {
                    if (ex.bizCode() == 409) {
                        conflict.incrementAndGet();
                    } else {
                        throw ex;
                    }
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            };
            Future<?> a = pool.submit(task);
            Future<?> b = pool.submit(task);
            start.countDown();
            a.get(20, TimeUnit.SECONDS);
            b.get(20, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }
        assertEquals(1, success.get());
        assertEquals(1, conflict.get());
        Integer matched = jdbc().queryForObject(
                "SELECT COUNT(*) FROM kjx_member_binding WHERE status = 'MATCHED'", Integer.class);
        assertEquals(1, matched);
    }

    @Test
    void transactionFaultRollsBackBindingAccountAndOutbox() {
        HostRecord host = createHost("KJX", "叩君芯");
        MemberRecord member = service().createMember(FixedDeptAccessPort.host(7, host.deptId()), host.deptId(),
                new CreateMemberCommand("zhangsan", 15, 435, EXPIRE, "", null), key(), false);
        long accountId = accounts().insertAccount();
        service().submitBinding(accountId,
                new SubmitBindingCommand("KJX", "zhangsan", member.memberCode(), 15), key(), "r", null, "d");
        long bindingId = jdbc().queryForObject(
                "SELECT id FROM kjx_member_binding WHERE account_id = ?", Long.class, accountId);
        useHook(new ReviewTransactionHook() {
            @Override
            public void afterAccountUpdated() {
                throw new IllegalStateException("injected fault");
            }
        });
        assertThrows(IllegalStateException.class,
                () -> service().approveBinding(ops, bindingId, "ok", key(), "r"));
        assertEquals("PENDING_REVIEW", jdbc().queryForObject(
                "SELECT status FROM kjx_member_binding WHERE id = ?", String.class, bindingId));
        assertEquals(0, jdbc().queryForObject(
                "SELECT bind_state FROM kjx_member WHERE id = ?", Integer.class, member.id()));
        assertEquals(AccountBindingView.BIND_PENDING, accounts().find(accountId).orElseThrow().bindStatus());
        assertEquals(0, jdbc().queryForObject("SELECT COUNT(*) FROM kjx_outbox_event", Integer.class));
    }

    @Test
    void hostCannotAccessOtherDeptMemberOrBinding() {
        HostRecord a = createHost("AAA", "主播A");
        HostRecord b = createHost("BBB", "主播B");
        MemberRecord memberB = service().createMember(FixedDeptAccessPort.host(11, b.deptId()), b.deptId(),
                new CreateMemberCommand("lisi", 8, 240, EXPIRE, "", null), key(), false);
        FixedDeptAccessPort hostA = FixedDeptAccessPort.host(10, a.deptId());
        assertEquals(403, biz(() -> service().getMember(hostA, memberB.id())));
        assertEquals(403, biz(() -> service().updateMember(hostA, memberB.id(),
                new UpdateMemberCommand(9, null, null, null, "越权"), key())));
        long accountId = accounts().insertAccount();
        service().submitBinding(accountId,
                new SubmitBindingCommand("BBB", "lisi", memberB.memberCode(), 8), key(), "r", null, "d");
        long bindingId = jdbc().queryForObject(
                "SELECT id FROM kjx_member_binding WHERE account_id = ?", Long.class, accountId);
        assertEquals(403, biz(() -> service().approveBinding(hostA, bindingId, "no", key(), "r")));
        assertTrue(service().pageMembers(hostA, null, null, null, null, null, 1, 20).list().isEmpty());
    }

    @Test
    void rejectUnbindAppealAndForceUnbind() {
        HostRecord host = createHost("KJX", "叩君芯");
        MemberRecord member = service().createMember(FixedDeptAccessPort.host(8, host.deptId()), host.deptId(),
                new CreateMemberCommand("zhangsan", 15, 435, EXPIRE, "", null), key(), false);
        long accountId = accounts().insertAccount();
        service().submitBinding(accountId,
                new SubmitBindingCommand("KJX", "zhangsan", member.memberCode(), 15), key(), "r", null, "d");
        long bindingId = jdbc().queryForObject(
                "SELECT id FROM kjx_member_binding WHERE account_id = ?", Long.class, accountId);
        service().rejectBinding(ops, bindingId, "资料不符", key(), "r");
        assertEquals(AccountBindingView.BIND_REJECTED, accounts().find(accountId).orElseThrow().bindStatus());

        service().submitBinding(accountId,
                new SubmitBindingCommand("KJX", "zhangsan", member.memberCode(), 15), key(), "r", null, "d");
        bindingId = jdbc().queryForObject(
                "SELECT id FROM kjx_member_binding WHERE account_id = ?", Long.class, accountId);
        service().approveBinding(ops, bindingId, "ok", key(), "r");
        Map<String, Object> appeal = service().submitAppeal(accountId,
                new MemberApplicationService.SubmitAppealCommand("WRONG_BIND", "误绑", "wechat", java.util.List.of()),
                key());
        assertEquals("PENDING", appeal.get("status"));
        long appealId = ((Number) appeal.get("appealId")).longValue();
        service().handleAppeal(ops, appealId, "APPROVED", "核对后解绑", key(), "r");
        assertEquals(0, jdbc().queryForObject(
                "SELECT bind_state FROM kjx_member WHERE id = ?", Integer.class, member.id()));
        assertEquals(AccountBindingView.BIND_NONE, accounts().find(accountId).orElseThrow().bindStatus());

        long account2 = accounts().insertAccount();
        service().submitBinding(account2,
                new SubmitBindingCommand("KJX", "zhangsan", member.memberCode(), 15), key(), "r", null, "d");
        long binding2 = jdbc().queryForObject(
                "SELECT id FROM kjx_member_binding WHERE account_id = ?", Long.class, account2);
        service().approveBinding(ops, binding2, "ok", key(), "r");
        service().forceUnbind(ops, binding2, "客服强制", key(), "r");
        assertEquals(0, jdbc().queryForObject(
                "SELECT COUNT(*) FROM kjx_member_binding WHERE id = ?", Integer.class, binding2));
        assertNotEquals(0, audit().entries().size());
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
}
