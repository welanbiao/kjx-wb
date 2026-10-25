package com.koujunxin.member.unlock;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.koujunxin.common.idempotent.IdempotencyCommand;
import com.koujunxin.common.idempotent.IdempotencyConflictException;
import com.koujunxin.common.idempotent.IdempotencyResult;
import com.koujunxin.common.idempotent.IdempotencyService;
import com.koujunxin.member.api.MemberException;
import com.koujunxin.member.api.MemberFailures;
import com.koujunxin.member.api.port.AccountBindingCommandPort;
import com.koujunxin.member.api.port.AccountBindingCommandPort.AccountBindingView;
import com.koujunxin.member.api.port.DeptAccessPort;
import com.koujunxin.member.api.port.MemberAuditPort;
import com.koujunxin.member.api.port.UnlockTransactionHook;
import com.koujunxin.member.domain.EntitlementRecord;
import com.koujunxin.member.domain.MemberRecord;
import com.koujunxin.member.domain.PageResult;
import com.koujunxin.member.domain.UnlockRecordRow;
import com.koujunxin.member.jdbc.EntitlementRepository;
import com.koujunxin.member.jdbc.MemberRepository;
import com.koujunxin.member.jdbc.UnlockRecordRepository;
import com.koujunxin.member.normalize.ExpireTimes;
import com.koujunxin.member.recalc.MemberRecalcService;
import com.koujunxin.work.api.WorkCatalogQueryPort;
import com.koujunxin.work.api.WorkPageDecoder;
import com.koujunxin.work.api.WorkSnapshot;

public class UnlockApplicationService {

    private static final Logger log = LoggerFactory.getLogger(UnlockApplicationService.class);
    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    public static final String SCENE_UNLOCK = "unlock";
    public static final String SCENE_ADJUST = "entitlement_adjust";
    public static final String SCENE_PURCHASE = "page_purchase";
    public static final String PAGE_PURCHASE_WORK = "breakup-editor";
    private static final Set<String> PAGE_PURCHASE_WORKS = Set.of(
            "breakup-editor",
            "dream-phone");
    static final long PAGE_PURCHASE_MEMBER_BASE = 1_000_000_000_000L;

    private final Clock clock;
    private final TransactionTemplate tx;
    private final ObjectMapper objectMapper;
    private final MemberRecalcService recalc;
    private final MemberRepository members;
    private final EntitlementRepository entitlements;
    private final UnlockRecordRepository records;
    private final AccountBindingCommandPort accounts;
    private final WorkCatalogQueryPort works;
    private final NewUnlockPolicy newUnlockPolicy;
    private final MemberAuditPort audit;
    private final UnlockOutbox outbox;
    private final IdempotencyService idempotency;
    private final RedisUnlockFailMerger failMerger;
    private final UnlockTransactionHook hook;

    public UnlockApplicationService(
            Clock clock,
            TransactionTemplate tx,
            ObjectMapper objectMapper,
            MemberRecalcService recalc,
            MemberRepository members,
            EntitlementRepository entitlements,
            UnlockRecordRepository records,
            AccountBindingCommandPort accounts,
            WorkCatalogQueryPort works,
            NewUnlockPolicy newUnlockPolicy,
            MemberAuditPort audit,
            UnlockOutbox outbox,
            IdempotencyService idempotency,
            RedisUnlockFailMerger failMerger,
            UnlockTransactionHook hook) {
        this.clock = clock == null ? Clock.system(SHANGHAI) : clock;
        this.tx = tx;
        this.objectMapper = objectMapper;
        this.recalc = recalc;
        this.members = members;
        this.entitlements = entitlements;
        this.records = records;
        this.accounts = accounts;
        this.works = works;
        this.newUnlockPolicy = newUnlockPolicy;
        this.audit = audit;
        this.outbox = outbox;
        this.idempotency = idempotency;
        this.failMerger = failMerger;
        this.hook = hook == null ? new UnlockTransactionHook() {
        } : hook;
    }

    public UnlockOutcome unlock(long accountId, String workCode, String idemKey, String requestId,
                                String ip, String deviceId) {
        WorkSnapshot work = works.findByCode(workCode).orElseThrow(MemberFailures::notFound);
        Map<String, Object> request = Map.of("workCode", work.workCode(), "accountId", accountId);
        try {
            return runIdempotent(SCENE_UNLOCK, work.id(), accountId, idemKey, request, () ->
                    doUnlock(accountId, work, idemKey, requestId, fingerprint(ip, deviceId, requestId)));
        } catch (DuplicateKeyException ex) {
            Optional<EntitlementRecord> existing = entitlements.findAccessible(accountId, work.id());
            if (existing.isPresent()) {
                MemberRecord member = members.findById(existing.get().memberId()).orElseThrow(MemberFailures::notFound);
                log.info("unlock duplicate-key rolled back accountId={} workId={}", accountId, work.id());
                return UnlockOutcome.already(unlockView(work, member, true));
            }
            throw ex;
        } catch (MemberException ex) {
            recordFailureIfNeeded(accountId, work, ex);
            throw ex;
        }
    }

    public UnlockOutcome purchase(long accountId, String workCode, String idemKey, String requestId) {
        String code = workCode == null ? "" : workCode.trim();
        if (!PAGE_PURCHASE_WORKS.contains(code)) {
            throw MemberFailures.badRequest("该作品不支持页面购买");
        }
        WorkSnapshot work = works.findByCode(code).orElseThrow(MemberFailures::notFound);
        Map<String, Object> request = Map.of("workCode", work.workCode(), "accountId", accountId, "source", EntitlementRecord.PURCHASE);
        try {
            return runIdempotent(SCENE_PURCHASE, work.id(), accountId, idemKey, request, () ->
                    doPurchase(accountId, work));
        } catch (DuplicateKeyException ex) {
            Optional<EntitlementRecord> existing = entitlements.findAccessible(accountId, work.id());
            if (existing.isPresent()) {
                MemberRecord member = members.findById(existing.get().memberId()).orElse(null);
                log.info("purchase duplicate-key rolled back accountId={} workId={}", accountId, work.id());
                return UnlockOutcome.already(purchaseView(work, member, true));
            }
            throw ex;
        }
    }

    public PageResult<Map<String, Object>> listMine(long accountId, int pageNo, int pageSize) {
        PageResult<EntitlementRecord> page = entitlements.pageByAccount(accountId, pageNo, pageSize);
        List<Map<String, Object>> list = new ArrayList<>();
        for (EntitlementRecord row : page.list()) {
            WorkSnapshot work = works.findById(row.workId()).orElse(null);
            if (work == null || work.deleted()) {
                continue;
            }
            list.add(entitlementItem(row, work));
        }
        return new PageResult<>(list, page.total());
    }

    public MemberRecord adjust(DeptAccessPort access, long memberId, String action, String workCode, String reason,
                               String idemKey, String requestId) {
        requireOps(access);
        requireReason(reason);
        if (action == null || action.isBlank()) {
            throw MemberFailures.badRequest("action 必填");
        }
        String normalized = action.trim().toUpperCase();
        if (!"ADD".equals(normalized) && !"REMOVE".equals(normalized)) {
            throw MemberFailures.badRequest("action 必须为 ADD 或 REMOVE");
        }
        WorkSnapshot work = works.findByCode(workCode).orElseThrow(MemberFailures::notFound);
        long actorId = access.requireActorId();
        Map<String, Object> payload = Map.of(
                "action", normalized,
                "workCode", work.workCode(),
                "reason", reason);
        return runIdempotent(SCENE_ADJUST, memberId, actorId, idemKey, payload, () -> {
            MemberRecord member = members.lockById(memberId).orElseThrow(MemberFailures::notFound);
            access.requireAccess(member.deptId());
            Instant occurredAt = Instant.now(clock);
            LocalDateTime now = LocalDateTime.now(clock.withZone(SHANGHAI));
            if ("ADD".equals(normalized)) {
                return officialAdd(member, work, reason, actorId, requestId, idemKey, occurredAt, now);
            }
            return officialRemove(member, work, reason, actorId, requestId, idemKey, occurredAt, now);
        });
    }

    public MemberRecord recalcFromFacts(DeptAccessPort access, long memberId, String reason, String idemKey) {
        requireOps(access);
        requireReason(reason);
        return runIdempotent("member_recalc_facts", memberId, access.requireActorId(), idemKey,
                Map.of("reason", reason), () -> {
                    MemberRecord existing = members.lockById(memberId).orElseThrow(MemberFailures::notFound);
                    access.requireAccess(existing.deptId());
                    MemberRecord updated = applyFactRecalc(existing, String.valueOf(access.requireActorId()));
                    audit.record("member", memberId, existing.deptId(), "RECALC", reason, access.requireActorId());
                    return updated;
                });
    }

    public PageResult<UnlockRecordRow> pageRecords(DeptAccessPort access, Long deptId, Long memberId, String workCode,
                                                   String result, String source, int pageNo, int pageSize) {
        if (!access.ops() && !access.host()) {
            throw MemberFailures.forbidden();
        }
        Long workId = resolveWorkId(workCode);
        if (workCode != null && !workCode.isBlank() && workId == null) {
            return new PageResult<>(List.of(), 0);
        }
        Long scoped = scopedDept(access, deptId);
        if (!access.ops()) {
            scoped = access.requireDeptId();
        }
        return records.page(scoped, memberId, workId, result, source, pageNo, pageSize);
    }

    public List<UnlockRecordRow> exportRecords(DeptAccessPort access, Long deptId, Long memberId, String result,
                                               String source) {
        requireOps(access);
        Long scoped = scopedDept(access, deptId);
        if (scoped == null && !access.ops()) {
            scoped = access.requireDeptId();
        }
        if (scoped != null) {
            access.requireAccess(scoped);
        }
        return records.listForExport(scoped, memberId, result, source);
    }

    public UnlockTransactionHook hook() {
        return hook;
    }

    private UnlockOutcome doPurchase(long accountId, WorkSnapshot work) {
        Optional<EntitlementRecord> existing = entitlements.findAccessible(accountId, work.id());
        if (existing.isPresent()) {
            MemberRecord member = members.findById(existing.get().memberId()).orElse(null);
            return UnlockOutcome.already(purchaseView(work, member, true));
        }
        AccountBindingView account = accounts.find(accountId).orElseThrow(MemberFailures::unauthorized);
        if (account.cancelling()) {
            throw MemberFailures.cancelling();
        }
        MemberRecord member = members.findBoundByAccount(accountId)
                .or(() -> members.findByBoundAccountId(accountId))
                .orElse(null);
        long deptId = member == null ? 0L : member.deptId();
        long memberId = member == null ? PAGE_PURCHASE_MEMBER_BASE + accountId : member.id();
        LocalDateTime now = LocalDateTime.now(clock.withZone(SHANGHAI));
        entitlements.insert(new EntitlementRecord(
                0L, deptId, memberId, accountId, work.id(), work.currentVersion(),
                EntitlementRecord.PURCHASE, null, now));
        log.info("page purchase succeeded accountId={} workId={} source={}", accountId, work.id(), EntitlementRecord.PURCHASE);
        return UnlockOutcome.created(purchaseView(work, member, false));
    }

    private UnlockOutcome doUnlock(long accountId, WorkSnapshot work, String idemKey, String requestId,
                                   String fingerprint) {
        Optional<EntitlementRecord> existing = entitlements.findAccessible(accountId, work.id());
        if (existing.isPresent()) {
            MemberRecord member = members.findById(existing.get().memberId()).orElseThrow(MemberFailures::notFound);
            return UnlockOutcome.already(unlockView(work, member, true));
        }
        AccountBindingView account = accounts.find(accountId).orElseThrow(MemberFailures::unauthorized);
        MemberRecord member = members.findBoundByAccount(accountId).orElseThrow(MemberFailures::bindingRequired);
        newUnlockPolicy.require(member, account, work, accountId);

        int cas = members.casIncrementUnlocked(member.id());
        hook.afterCas();
        if (cas == 0) {
            MemberRecord latest = members.findById(member.id()).orElse(member);
            throw classifyCasMiss(latest);
        }

        MemberRecord after = members.findById(member.id()).orElseThrow(MemberFailures::notFound);
        int beforeUnlocked = after.unlockedCount() - 1;
        int afterUnlocked = after.unlockedCount();
        int beforeRemain = after.remainCount() + 1;
        int afterRemain = after.remainCount();
        LocalDateTime now = LocalDateTime.now(clock.withZone(SHANGHAI));
        Instant occurredAt = Instant.now(clock);

        long recordId = records.insert(new UnlockRecordRow(
                0L, after.deptId(), after.id(), after.memberCode(), after.douyinId(), accountId,
                work.id(), work.name(), work.currentVersion(), now,
                beforeUnlocked, afterUnlocked, beforeRemain, afterRemain,
                UnlockRecordRow.SUCCESS, null, UnlockRecordRow.USER, null, fingerprint, idemKey));
        hook.afterUnlockRecordInserted();

        hook.beforeEntitlementInsert();
        long entitlementId = entitlements.insert(new EntitlementRecord(
                0L, after.deptId(), after.id(), accountId, work.id(), work.currentVersion(),
                EntitlementRecord.USER, recordId, now));
        hook.afterEntitlementInserted();

        audit.record("entitlement", entitlementId, after.deptId(), "UNLOCK", "用户解锁", accountId);
        hook.afterAudit();
        outbox.granted(entitlementId, recordId, accountId, after.id(), after.deptId(), work.id(), work.workCode(),
                EntitlementRecord.USER, work.currentVersion(),
                beforeUnlocked, afterUnlocked, beforeRemain, afterRemain, occurredAt, requestId, idemKey);
        hook.afterOutbox();
        log.info("unlock succeeded accountId={} workId={} entitlementId={}", accountId, work.id(), entitlementId);
        return UnlockOutcome.created(unlockView(work, after, false));
    }

    private MemberRecord officialAdd(MemberRecord member, WorkSnapshot work, String reason, long actorId,
                                     String requestId, String idemKey, Instant occurredAt, LocalDateTime now) {
        if (entitlements.findByMemberAndWork(member.id(), work.id()).isPresent()) {
            throw MemberFailures.conflict("权益已存在");
        }
        int beforeUnlocked = entitlements.countByMemberId(member.id());
        long recordId = records.insert(new UnlockRecordRow(
                0L, member.deptId(), member.id(), member.memberCode(), member.douyinId(), member.boundAccountId(),
                work.id(), work.name(), work.currentVersion(), now,
                beforeUnlocked, beforeUnlocked + 1,
                Math.max(0, member.availableCount() - beforeUnlocked),
                Math.max(0, member.availableCount() - beforeUnlocked - 1),
                UnlockRecordRow.SUCCESS, null, UnlockRecordRow.OFFICIAL_ADD, actorId, null, idemKey));
        long entitlementId = entitlements.insert(new EntitlementRecord(
                0L, member.deptId(), member.id(), member.boundAccountId(), work.id(), work.currentVersion(),
                EntitlementRecord.OFFICIAL_ADD, recordId, now));
        MemberRecord updated = applyFactRecalc(member, String.valueOf(actorId));
        audit.record("entitlement", entitlementId, member.deptId(), "OFFICIAL_ADD", reason, actorId);
        outbox.granted(entitlementId, recordId, member.boundAccountId(),
                member.id(), member.deptId(), work.id(), work.workCode(), EntitlementRecord.OFFICIAL_ADD,
                work.currentVersion(),
                beforeUnlocked, updated.unlockedCount(), member.remainCount(), updated.remainCount(),
                occurredAt, requestId, idemKey);
        log.info("official add entitlement memberId={} workId={}", member.id(), work.id());
        return updated;
    }

    private MemberRecord officialRemove(MemberRecord member, WorkSnapshot work, String reason, long actorId,
                                        String requestId, String idemKey, Instant occurredAt, LocalDateTime now) {
        EntitlementRecord existing = entitlements.findByMemberAndWork(member.id(), work.id())
                .orElseThrow(MemberFailures::notFound);
        int beforeUnlocked = entitlements.countByMemberId(member.id());
        entitlements.deleteByMemberAndWork(member.id(), work.id());
        long recordId = records.insert(new UnlockRecordRow(
                0L, member.deptId(), member.id(), member.memberCode(), member.douyinId(), member.boundAccountId(),
                work.id(), work.name(), work.currentVersion(), now,
                beforeUnlocked, Math.max(0, beforeUnlocked - 1),
                Math.max(0, member.availableCount() - beforeUnlocked),
                Math.max(0, member.availableCount() - beforeUnlocked + 1),
                UnlockRecordRow.SUCCESS, null, UnlockRecordRow.OFFICIAL_REMOVE, actorId, null, idemKey));
        MemberRecord updated = applyFactRecalc(member, String.valueOf(actorId));
        audit.record("entitlement", existing.id(), member.deptId(), "OFFICIAL_REMOVE", reason, actorId);
        outbox.revoked(existing.id(), recordId, member.boundAccountId(),
                member.id(), member.deptId(), work.id(), work.workCode(), UnlockRecordRow.OFFICIAL_REMOVE,
                work.currentVersion(),
                beforeUnlocked, updated.unlockedCount(), member.remainCount(), updated.remainCount(),
                occurredAt, requestId, idemKey);
        log.info("official remove entitlement memberId={} workId={}", member.id(), work.id());
        return updated;
    }

    private MemberRecord applyFactRecalc(MemberRecord member, String actor) {
        int unlocked = entitlements.countByMemberId(member.id());
        LocalDateTime startAt = ExpireTimes.startAt(member.expireAt(), member.subscribedDays());
        var result = recalc.recalc(member.subscribedDays(), unlocked, startAt, member.expireAt(),
                LocalDateTime.now(clock.withZone(SHANGHAI)));
        members.applyRecalc(member.id(), result, startAt, actor);
        return members.findById(member.id()).orElseThrow(MemberFailures::notFound);
    }

    private MemberException classifyCasMiss(MemberRecord member) {
        LocalDateTime now = LocalDateTime.now(clock.withZone(SHANGHAI));
        String status = recalc.status(member.startAt(), member.expireAt(), now);
        if ("NOT_STARTED".equals(status)) {
            return MemberFailures.notStarted(ExpireTimes.format(member.startAt()));
        }
        if ("EXPIRED".equals(status)) {
            return MemberFailures.expired();
        }
        return MemberFailures.quotaExhausted();
    }

    private void recordFailureIfNeeded(long accountId, WorkSnapshot work, MemberException ex) {
        int code = ex.bizCode();
        if (code != 1005 && code != 1006 && code != 1007 && code != 1008 && code != 1009 && code != 1014) {
            return;
        }
        Optional<MemberRecord> member = members.findBoundByAccount(accountId);
        if (member.isEmpty()) {
            return;
        }
        MemberRecord row = member.get();
        String reason = failReasonOf(code);
        if (failMerger != null && !failMerger.shouldPersist(row.id(), work.id(), reason)) {
            log.info("unlock fail merged accountId={} workId={} code={}", accountId, work.id(), code);
            return;
        }
        try {
            records.insert(new UnlockRecordRow(
                    0L, row.deptId(), row.id(), row.memberCode(), row.douyinId(), accountId,
                    work.id(), work.name(), work.currentVersion(),
                    LocalDateTime.now(clock.withZone(SHANGHAI)),
                    row.unlockedCount(), row.unlockedCount(), row.remainCount(), row.remainCount(),
                    UnlockRecordRow.FAILED, reason, UnlockRecordRow.USER, null, null, null));
        } catch (RuntimeException persistEx) {
            log.warn("unlock fail persist skipped accountId={} workId={} code={}", accountId, work.id(), code);
        }
    }

    private static String failReasonOf(int code) {
        return switch (code) {
            case 1005 -> "MEMBER_NOT_STARTED";
            case 1006 -> "MEMBER_EXPIRED";
            case 1007 -> "UNLOCK_QUOTA_EXHAUSTED";
            case 1008 -> "WORK_UNPUBLISHED";
            case 1009 -> "BINDING_REQUIRED";
            case 1014 -> "ACCOUNT_CANCELLING";
            default -> "FAILED";
        };
    }

    private Map<String, Object> purchaseView(WorkSnapshot work, MemberRecord member, boolean already) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("workCode", work.workCode());
        data.put("name", WorkPageDecoder.name(work.workCode(), work.name()));
        data.put("unlocked", true);
        data.put("unlockedCount", member == null ? 0 : member.unlockedCount());
        data.put("remainCount", member == null ? 0 : member.remainCount());
        data.put("entryUrl", "/runtime/" + work.workCode());
        data.put("alreadyUnlocked", already);
        data.put("source", EntitlementRecord.PURCHASE);
        return data;
    }

    private Map<String, Object> unlockView(WorkSnapshot work, MemberRecord member, boolean already) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("workCode", work.workCode());
        data.put("unlockedCount", member.unlockedCount());
        data.put("remainCount", member.remainCount());
        data.put("entryUrl", "/runtime/" + work.workCode());
        data.put("alreadyUnlocked", already);
        return data;
    }

    private Map<String, Object> entitlementItem(EntitlementRecord row, WorkSnapshot work) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("workCode", work.workCode());
        data.put("name", WorkPageDecoder.name(work.workCode(), work.name()));
        data.put("coverUrl", work.coverUrl());
        data.put("workType", work.workType() == null ? null : work.workType().name());
        data.put("workStatus", work.status() == null ? null : work.status().name());
        data.put("unlockedAt", ExpireTimes.format(row.unlockedAt()));
        data.put("source", row.source());
        data.put("entryUrl", "/runtime/" + work.workCode());
        return data;
    }

    private Long resolveWorkId(String workCode) {
        if (workCode == null || workCode.isBlank()) {
            return null;
        }
        return works.findByCode(workCode).map(WorkSnapshot::id).orElse(null);
    }

    private Long scopedDept(DeptAccessPort access, Long deptId) {
        if (deptId != null) {
            access.requireAccess(deptId);
            return deptId;
        }
        return null;
    }

    private static void requireOps(DeptAccessPort access) {
        if (!access.ops()) {
            throw MemberFailures.forbidden();
        }
    }

    private static void requireReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw MemberFailures.badRequest("reason 必填");
        }
    }

    private static String fingerprint(String ip, String deviceId, String requestId) {
        String raw = String.join("|",
                ip == null ? "" : ip.trim(),
                deviceId == null ? "" : deviceId.trim(),
                requestId == null ? "" : requestId.trim());
        return raw.length() > 128 ? raw.substring(0, 128) : raw;
    }

    @SuppressWarnings("unchecked")
    private <T> T runIdempotent(String scene, long refId, long actorId, String idemKey, Object payload,
                                java.util.function.Supplier<T> action) {
        if (idemKey == null || idemKey.isBlank()) {
            throw MemberFailures.badRequest("X-Idempotency-Key 必填");
        }
        T executed = tx.execute(status -> {
            try {
                AtomicReference<T> first = new AtomicReference<>();
                IdempotencyResult result = idempotency.execute(
                        IdempotencyCommand.of(scene, refId, idemKey, payload).withActor(actorId),
                        () -> {
                            T value = action.get();
                            first.set(value);
                            if (value instanceof UnlockOutcome outcome) {
                                return IdempotencyResult.of(String.valueOf(outcome.code()), json(outcome.replayBody()));
                            }
                            if (value instanceof MemberRecord member) {
                                return IdempotencyResult.of("OK", json(Map.of("k", "member", "id", member.id())));
                            }
                            return IdempotencyResult.of("OK", json(value));
                        });
                if (first.get() != null) {
                    return first.get();
                }
                return (T) decodeReplay(result);
            } catch (IdempotencyConflictException ex) {
                throw MemberFailures.idempotencyConflict();
            }
        });
        if (executed == null) {
            throw new IllegalStateException("transaction returned null");
        }
        return executed;
    }

    @SuppressWarnings("unchecked")
    private Object decodeReplay(IdempotencyResult result) {
        try {
            Map<String, Object> map = objectMapper.readValue(result.resultBodyJson(), Map.class);
            if (map.containsKey("workCode") && map.containsKey("alreadyUnlocked")) {
                return UnlockOutcome.already(map);
            }
            if ("member".equals(map.get("k"))) {
                long id = ((Number) map.get("id")).longValue();
                return members.findById(id).orElseThrow(MemberFailures::notFound);
            }
            return map;
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("idempotency replay decode failed", ex);
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("json failed", ex);
        }
    }
}
