package com.koujunxin.member.application;

import java.net.InetAddress;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
import com.koujunxin.member.api.port.HostDeptPort;
import com.koujunxin.member.api.port.MemberAuditPort;
import com.koujunxin.member.api.port.ReviewTransactionHook;
import com.koujunxin.member.bind.RedisBindAttemptStore;
import com.koujunxin.member.code.MemberCodeGenerator;
import com.koujunxin.member.domain.AppealRecord;
import com.koujunxin.member.domain.BindingRecord;
import com.koujunxin.member.domain.HostRecord;
import com.koujunxin.member.domain.MemberRecord;
import com.koujunxin.member.domain.PageResult;
import com.koujunxin.member.jdbc.AppealRepository;
import com.koujunxin.member.jdbc.BindingRepository;
import com.koujunxin.member.jdbc.HostRepository;
import com.koujunxin.member.jdbc.MemberRepository;
import com.koujunxin.member.normalize.DouyinIds;
import com.koujunxin.member.normalize.ExpireTimes;
import com.koujunxin.member.normalize.MemberCodes;
import com.koujunxin.member.notify.BindingOutbox;
import com.koujunxin.member.port.JdbcBoundMemberQueryPort;
import com.koujunxin.member.recalc.MemberRecalcService;

public class MemberApplicationService {

    private static final Logger log = LoggerFactory.getLogger(MemberApplicationService.class);
    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    private static final int CODE_RETRY = 8;
    public static final int FAIL_LIMIT = 3;

    private final Clock clock;
    private final TransactionTemplate tx;
    private final ObjectMapper objectMapper;
    private final MemberRecalcService recalc;
    private final MemberCodeGenerator codes;
    private final HostRepository hosts;
    private final MemberRepository members;
    private final BindingRepository bindings;
    private final AppealRepository appeals;
    private final AccountBindingCommandPort accounts;
    private final HostDeptPort hostDepts;
    private final MemberAuditPort audit;
    private final BindingOutbox outbox;
    private final IdempotencyService idempotency;
    private final RedisBindAttemptStore attempts;
    private final JdbcBoundMemberQueryPort snapshots;
    private final ReviewTransactionHook hook;
    private final Duration lockTtl;

    public MemberApplicationService(
            Clock clock,
            TransactionTemplate tx,
            ObjectMapper objectMapper,
            MemberRecalcService recalc,
            MemberCodeGenerator codes,
            HostRepository hosts,
            MemberRepository members,
            BindingRepository bindings,
            AppealRepository appeals,
            AccountBindingCommandPort accounts,
            HostDeptPort hostDepts,
            MemberAuditPort audit,
            BindingOutbox outbox,
            IdempotencyService idempotency,
            RedisBindAttemptStore attempts,
            JdbcBoundMemberQueryPort snapshots,
            ReviewTransactionHook hook,
            Duration lockTtl) {
        this.clock = clock;
        this.tx = tx;
        this.objectMapper = objectMapper;
        this.recalc = recalc;
        this.codes = codes;
        this.hosts = hosts;
        this.members = members;
        this.bindings = bindings;
        this.appeals = appeals;
        this.accounts = accounts;
        this.hostDepts = hostDepts;
        this.audit = audit;
        this.outbox = outbox;
        this.idempotency = idempotency;
        this.attempts = attempts;
        this.snapshots = snapshots;
        this.hook = hook == null ? new ReviewTransactionHook() {
        } : hook;
        this.lockTtl = lockTtl == null ? Duration.ofMinutes(30) : lockTtl;
    }

    public MemberRecalcService recalcService() {
        return recalc;
    }

    public JdbcBoundMemberQueryPort snapshots() {
        return snapshots;
    }

    public HostRecord createHost(DeptAccessPort access, CreateHostCommand command, String idemKey, String requestId) {
        requireReason(command.reason());
        if (!access.ops()) {
            throw MemberFailures.forbidden();
        }
        if (command.adminUserId() == null || command.adminUserId() <= 0) {
            throw MemberFailures.badRequest("adminUserId 必填");
        }
        String hostCode = MemberCodes.requireHostCode(command.hostCode());
        return runIdempotent("host_create", hostCode.hashCode() & 0xffff_ffffL, access.requireActorId(),
                idemKey, command, () -> {
                    if (hosts.findByHostCode(hostCode).isPresent()) {
                        throw MemberFailures.conflict("主播短码已存在");
                    }
                    long deptId = hostDepts.createDepartment(
                            command.hostName(), command.parentDeptId(), command.adminUserId());
                    try {
                        hosts.insert(new HostRecord(deptId, hostCode, command.dyAccount(), command.logoUrl(),
                                command.contact(), command.remark(), now()), String.valueOf(access.requireActorId()));
                    } catch (DuplicateKeyException ex) {
                        throw MemberFailures.conflict("主播短码已存在");
                    }
                    audit.record("host", deptId, deptId, "CREATE", command.reason(), access.requireActorId());
                    return hosts.findByDeptId(deptId).orElseThrow(MemberFailures::notFound);
                });
    }

    public HostRecord updateHost(DeptAccessPort access, long deptId, UpdateHostCommand command, String idemKey) {
        requireReason(command.reason());
        access.requireAccess(deptId);
        return runIdempotent("host_update", deptId, access.requireActorId(), idemKey, command, () -> {
            HostRecord existing = hosts.findByDeptId(deptId).orElseThrow(MemberFailures::notFound);
            hosts.update(deptId, command.dyAccount(), command.logoUrl(), command.contact(), command.remark(),
                    String.valueOf(access.requireActorId()));
            if (command.hostName() != null && !command.hostName().isBlank()) {
                hostDepts.rename(deptId, command.hostName());
            }
            audit.record("host", deptId, existing.deptId(), "UPDATE", command.reason(), access.requireActorId());
            return hosts.findByDeptId(deptId).orElseThrow(MemberFailures::notFound);
        });
    }

    public HostRecord getHost(DeptAccessPort access, long deptId) {
        access.requireAccess(deptId);
        return hosts.findByDeptId(deptId).orElseThrow(MemberFailures::notFound);
    }

    public PageResult<HostRecord> pageHosts(DeptAccessPort access, String hostCode, int pageNo, int pageSize) {
        if (!access.ops()) {
            throw MemberFailures.forbidden();
        }
        return hosts.page(hostCode, pageNo, pageSize);
    }

    public HostRecord selfHost(DeptAccessPort access) {
        long deptId = access.requireDeptId();
        return hosts.findByDeptId(deptId).orElseThrow(MemberFailures::notFound);
    }

    public List<HostRecord> listHosts() {
        return hosts.listAll();
    }

    public String hostName(HostRecord host) {
        return hostDepts.findName(host.deptId()).orElse(host.hostCode());
    }

    public MemberRecord createMember(DeptAccessPort access, long deptId, CreateMemberCommand command,
                                     String idemKey, boolean requireReason) {
        if (requireReason) {
            requireReason(command.reason());
        }
        access.requireAccess(deptId);
        HostRecord host = hosts.findByDeptId(deptId).orElseThrow(MemberFailures::notFound);
        String actor = String.valueOf(access.requireActorId());
        Map<String, Object> payload = Map.of(
                "deptId", deptId,
                "douyinId", command.douyinId(),
                "subscribedMonths", command.subscribedMonths(),
                "subscribedDays", command.subscribedDays(),
                "expireAt", command.expireAt());
        return runIdempotent("member_create", deptId, access.requireActorId(), idemKey, payload, () -> {
            MemberRecord created = insertMember(host, command, actor);
            if (command.reason() != null && !command.reason().isBlank()) {
                audit.record("member", created.id(), deptId, "CREATE", command.reason(), access.requireActorId());
            }
            return created;
        });
    }

    public MemberRecord updateMember(DeptAccessPort access, long memberId, UpdateMemberCommand command, String idemKey) {
        requireReason(command.reason());
        return runIdempotent("member_update", memberId, access.requireActorId(), idemKey, command, () -> {
            MemberRecord existing = members.lockById(memberId).orElseThrow(MemberFailures::notFound);
            access.requireAccess(existing.deptId());
            int days = command.subscribedDays() == null ? existing.subscribedDays() : command.subscribedDays();
            LocalDateTime expireAt = command.expireAt() == null ? existing.expireAt() : command.expireAt();
            LocalDateTime startAt = ExpireTimes.startAt(expireAt, days);
            var result = recalc.recalc(days, existing.unlockedCount(), startAt, expireAt, now());
            members.updateFields(memberId, command.subscribedMonths(), command.subscribedDays(), command.expireAt(),
                    command.remark(), result, startAt, String.valueOf(access.requireActorId()));
            audit.record("member", memberId, existing.deptId(), "UPDATE", command.reason(), access.requireActorId());
            MemberRecord updated = members.findById(memberId).orElseThrow(MemberFailures::notFound);
            if (updated.bound() && updated.boundAccountId() != null) {
                outbox.memberUpdated(updated.id(), updated.boundAccountId(), updated.deptId(), null, idemKey);
            }
            return updated;
        });
    }

    public MemberRecord getMember(DeptAccessPort access, long memberId) {
        MemberRecord member = members.findById(memberId).orElseThrow(MemberFailures::notFound);
        access.requireAccess(member.deptId());
        return member;
    }

    public String getMemberCode(DeptAccessPort access, long memberId) {
        return getMember(access, memberId).memberCode();
    }

    public PageResult<MemberRecord> pageMembers(DeptAccessPort access, Long deptId, String douyinId, String memberCode,
                                                String status, Integer bindState, int pageNo, int pageSize) {
        Long scoped = scopedDept(access, deptId);
        String dy = (douyinId == null || douyinId.isBlank()) ? null : DouyinIds.normalize(douyinId);
        return members.page(scoped, dy, memberCode, status, bindState, pageNo, pageSize);
    }

    public MemberRecord recalcMember(DeptAccessPort access, long memberId, String reason, String idemKey) {
        requireReason(reason);
        return runIdempotent("member_recalc", memberId, access.requireActorId(), idemKey, Map.of("reason", reason),
                () -> {
                    MemberRecord existing = members.lockById(memberId).orElseThrow(MemberFailures::notFound);
                    access.requireAccess(existing.deptId());
                    LocalDateTime startAt = ExpireTimes.startAt(existing.expireAt(), existing.subscribedDays());
                    var result = recalc.recalc(existing.subscribedDays(), existing.unlockedCount(), startAt,
                            existing.expireAt(), now());
                    members.applyRecalc(memberId, result, startAt, String.valueOf(access.requireActorId()));
                    audit.record("member", memberId, existing.deptId(), "RECALC", reason, access.requireActorId());
                    return members.findById(memberId).orElseThrow(MemberFailures::notFound);
                });
    }

    public List<MemberRecord> exportMembers(DeptAccessPort access, Long deptId, String status) {
        long scoped = access.ops() && deptId != null ? deptId : access.requireDeptId();
        access.requireAccess(scoped);
        return members.listForExport(scoped, status);
    }

    public Map<String, Object> submitBinding(long accountId, SubmitBindingCommand command, String idemKey,
                                             String requestId, String ip, String deviceId) {
        AccountBindingView account = accounts.find(accountId).orElseThrow(MemberFailures::unauthorized);
        if (account.cancelling()) {
            throw MemberFailures.cancelling();
        }
        if (isBindLocked(accountId, account)) {
            throw MemberFailures.locked();
        }
        Map<String, Object> payload = Map.of(
                "hostCode", command.hostCode() == null ? "" : command.hostCode(),
                "douyinId", command.douyinId() == null ? "" : command.douyinId(),
                "memberCode", command.memberCode() == null ? "" : command.memberCode(),
                "claimMonths", command.claimMonths());
        try {
            return runIdempotent("binding", accountId, accountId, idemKey, payload, () -> {
                AccountBindingView fresh = accounts.find(accountId).orElseThrow(MemberFailures::unauthorized);
                if (isBindLocked(accountId, fresh)) {
                    throw MemberFailures.locked();
                }
                if (fresh.bindStatus() == AccountBindingView.BIND_MATCHED) {
                    throw MemberFailures.alreadyBound();
                }
                if (command.claimMonths() < 1 || command.claimMonths() > 120) {
                    throw MemberFailures.badRequest("claimMonths 必填");
                }
                Optional<BindingRecord> existing = bindings.findByAccountId(accountId);
                if (existing.isPresent() && BindingRecord.MATCHED.equals(existing.get().status())) {
                    throw MemberFailures.alreadyBound();
                }
                String douyin = DouyinIds.normalize(command.douyinId());
                String compact = MemberCodes.compact(command.memberCode());
                HostRecord host = resolveHost(command.hostCode(), compact);
                Optional<MemberRecord> matched = members.matchTriple(host.deptId(), compact, douyin, command.claimMonths());
                if (matched.isEmpty()) {
                    throw MemberFailures.mismatch();
                }
                MemberRecord member = matched.get();
                if (member.bound() && (member.boundAccountId() == null || member.boundAccountId() != accountId)) {
                    throw MemberFailures.taken();
                }
                Optional<BindingRecord> byMember = bindings.findByMemberId(member.id());
                if (byMember.isPresent() && byMember.get().accountId() != accountId
                        && !BindingRecord.REJECTED.equals(byMember.get().status())) {
                    throw MemberFailures.taken();
                }
                bindings.deleteRejectedByMemberExcept(member.id(), accountId);
                BindingRecord pending = new BindingRecord(
                        0, member.deptId(), accountId, member.id(), douyin, command.douyinId(),
                        member.memberCode(), command.claimMonths(), BindingRecord.PENDING_REVIEW,
                        null, null, null, null, null, null, now());
                try {
                    if (existing.isPresent()) {
                        bindings.updatePending(existing.get().id(), pending, ipBytes(ip), deviceId);
                    } else {
                        bindings.insert(pending, ipBytes(ip), deviceId);
                    }
                } catch (DuplicateKeyException ex) {
                    throw MemberFailures.taken();
                }
                accounts.updateBindStatus(accountId, AccountBindingView.BIND_PENDING);
                accounts.resetFailures(accountId);
                if (attempts != null) {
                    attempts.reset(accountId);
                }
                log.info("binding submitted accountId={} memberId={} status={}", accountId, member.id(),
                        BindingRecord.PENDING_REVIEW);
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("status", BindingRecord.PENDING_REVIEW);
                data.put("member", null);
                return data;
            });
        } catch (MemberException ex) {
            if (ex.bizCode() == 1001) {
                persistMismatchOutsideTx(accountId);
                if (isBindLocked(accountId, accounts.find(accountId).orElse(account))) {
                    throw MemberFailures.locked();
                }
            }
            throw ex;
        }
    }

    public Map<String, Object> getBinding(long accountId) {
        Optional<BindingRecord> binding = bindings.findByAccountId(accountId);
        Map<String, Object> data = new LinkedHashMap<>();
        if (binding.isEmpty()) {
            data.put("status", BindingRecord.NONE);
            data.put("submitAt", null);
            data.put("rejectReason", null);
            data.put("member", null);
            return data;
        }
        BindingRecord row = binding.get();
        data.put("status", row.status());
        data.put("submitAt", ExpireTimes.format(row.createTime()));
        data.put("rejectReason", row.rejectReason());
        if (BindingRecord.MATCHED.equals(row.status())) {
            data.put("member", members.findById(row.memberId()).map(snapshots::snapshot).orElse(null));
        } else {
            data.put("member", null);
        }
        return data;
    }

    public Map<String, Object> memberProfile(long accountId) {
        return snapshots.findSnapshot(accountId).map(value -> (Map<String, Object>) value).orElse(null);
    }

    public PageResult<BindingRecord> pageBindings(DeptAccessPort access, Long deptId, String status, String memberCode,
                                                  int pageNo, int pageSize) {
        Long scoped = scopedDept(access, deptId);
        return bindings.page(scoped, status, memberCode, pageNo, pageSize);
    }

    public void approveBinding(DeptAccessPort access, long bindingId, String remark, String idemKey, String requestId) {
        runIdempotent("binding_approve", bindingId, access.requireActorId(), idemKey,
                Map.of("bindingId", bindingId, "remark", remark == null ? "" : remark),
                () -> {
                    doApprove(access, bindingId, remark, idemKey, requestId);
                    return Map.of("ok", true);
                });
    }

    public void rejectBinding(DeptAccessPort access, long bindingId, String reason, String idemKey, String requestId) {
        requireReason(reason);
        runIdempotent("binding_reject", bindingId, access.requireActorId(), idemKey, Map.of("reason", reason),
                () -> {
                    BindingRecord row = bindings.lockById(bindingId).orElseThrow(MemberFailures::notFound);
                    access.requireAccess(row.deptId());
                    int updated = bindings.casStatus(bindingId, BindingRecord.PENDING_REVIEW, BindingRecord.REJECTED,
                            access.requireActorId(), now(), reason, null);
                    if (updated == 0) {
                        throw MemberFailures.conflict("绑定已处理");
                    }
                    accounts.updateBindStatus(row.accountId(), AccountBindingView.BIND_REJECTED);
                    audit.record("binding", bindingId, row.deptId(), "REJECT", reason, access.requireActorId());
                    outbox.bindingRejected(bindingId, row.accountId(), row.memberId(), row.deptId(), requestId, idemKey);
                    log.info("binding rejected bindingId={} accountId={}", bindingId, row.accountId());
                    return Map.of("ok", true);
                });
    }

    public void forceUnbind(DeptAccessPort access, long bindingId, String reason, String idemKey, String requestId) {
        requireReason(reason);
        runIdempotent("binding_force_unbind", bindingId, access.requireActorId(), idemKey, Map.of("reason", reason),
                () -> {
                    completeUnbind(access, bindingId, reason, requestId, idemKey, true);
                    return Map.of("ok", true);
                });
    }

    public Map<String, Object> submitAppeal(long accountId, SubmitAppealCommand command, String idemKey) {
        AccountBindingView account = accounts.find(accountId).orElseThrow(MemberFailures::unauthorized);
        if (account.cancelling()) {
            throw MemberFailures.cancelling();
        }
        requireText(command.content(), "content");
        if (!List.of(AppealRecord.CHANGE_DOUYIN, AppealRecord.WRONG_BIND, AppealRecord.OTHER).contains(command.appealType())) {
            throw MemberFailures.badRequest("appealType 不合法");
        }
        return runIdempotent("appeal", accountId, accountId, idemKey, command, () -> {
            Optional<BindingRecord> binding = bindings.findByAccountId(accountId);
            Long bindingId = binding.map(BindingRecord::id).orElse(null);
            if (binding.isPresent() && BindingRecord.MATCHED.equals(binding.get().status())
                    && (AppealRecord.WRONG_BIND.equals(command.appealType())
                    || AppealRecord.CHANGE_DOUYIN.equals(command.appealType()))) {
                bindings.markUnbinding(binding.get().id(), now());
                accounts.updateBindStatus(accountId, AccountBindingView.BIND_UNBINDING);
            }
            long id = appeals.insert(new AppealRecord(
                    0, accountId, bindingId, command.appealType(), command.content(), command.contact(),
                    command.attachments(), AppealRecord.PENDING, null, null, null, now()));
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("appealId", id);
            data.put("status", AppealRecord.PENDING);
            return data;
        });
    }

    public PageResult<AppealRecord> pageAppeals(DeptAccessPort access, String status, int pageNo, int pageSize) {
        if (!access.ops()) {
            throw MemberFailures.forbidden();
        }
        return appeals.page(status, pageNo, pageSize);
    }

    public void handleAppeal(DeptAccessPort access, long appealId, String result, String handleNote,
                             String idemKey, String requestId) {
        requireReason(handleNote);
        if (!AppealRecord.APPROVED.equals(result) && !AppealRecord.REJECTED.equals(result)) {
            throw MemberFailures.badRequest("result 不合法");
        }
        runIdempotent("appeal_handle", appealId, access.requireActorId(), idemKey,
                Map.of("result", result, "handleNote", handleNote),
                () -> {
                    AppealRecord row = appeals.lockById(appealId).orElseThrow(MemberFailures::notFound);
                    int updated = appeals.casHandle(appealId, result, access.requireActorId(), now(), handleNote);
                    if (updated == 0) {
                        throw MemberFailures.conflict("申诉已处理");
                    }
                    if (row.bindingId() != null) {
                        BindingRecord binding = bindings.findById(row.bindingId()).orElse(null);
                        if (binding != null) {
                            access.requireAccess(binding.deptId());
                            if (AppealRecord.APPROVED.equals(result)
                                    && BindingRecord.UNBINDING.equals(binding.status())) {
                                completeUnbind(access, binding.id(), handleNote, requestId, idemKey, false);
                            } else if (AppealRecord.REJECTED.equals(result)
                                    && BindingRecord.UNBINDING.equals(binding.status())) {
                                bindings.casStatus(binding.id(), BindingRecord.UNBINDING, BindingRecord.MATCHED,
                                        access.requireActorId(), now(), null, binding.matchedAt());
                                accounts.updateBindStatus(binding.accountId(), AccountBindingView.BIND_MATCHED);
                            }
                        }
                    }
                    audit.record("appeal", appealId, 0, "HANDLE", handleNote, access.requireActorId());
                    return Map.of("ok", true);
                });
    }

    private void doApprove(DeptAccessPort access, long bindingId, String remark, String idemKey, String requestId) {
        BindingRecord row = bindings.lockById(bindingId).orElseThrow(MemberFailures::notFound);
        access.requireAccess(row.deptId());
        MemberRecord member = members.lockById(row.memberId()).orElseThrow(MemberFailures::notFound);
        if (member.bound() && member.boundAccountId() != null && member.boundAccountId() != row.accountId()) {
            throw MemberFailures.taken();
        }
        LocalDateTime now = now();
        int updated = bindings.casStatus(bindingId, BindingRecord.PENDING_REVIEW, BindingRecord.MATCHED,
                access.requireActorId(), now, remark, now);
        if (updated == 0) {
            throw MemberFailures.conflict("绑定已处理");
        }
        hook.afterBindingMatched();
        int bound = members.markBound(member.id(), row.accountId(), now);
        if (bound == 0) {
            throw MemberFailures.taken();
        }
        hook.afterMemberBound();
        accounts.updateBindStatus(row.accountId(), AccountBindingView.BIND_MATCHED);
        hook.afterAccountUpdated();
        audit.record("binding", bindingId, row.deptId(), "APPROVE",
                remark == null || remark.isBlank() ? "审核通过" : remark, access.requireActorId());
        hook.afterAudit();
        outbox.bindingApproved(bindingId, row.accountId(), row.memberId(), row.deptId(), requestId, idemKey);
        hook.afterOutbox();
        log.info("binding approved bindingId={} accountId={} memberId={}", bindingId, row.accountId(), row.memberId());
    }

    private void completeUnbind(DeptAccessPort access, long bindingId, String reason, String requestId,
                                String idemKey, boolean force) {
        BindingRecord row = bindings.lockById(bindingId).orElseThrow(MemberFailures::notFound);
        access.requireAccess(row.deptId());
        if (!BindingRecord.MATCHED.equals(row.status()) && !BindingRecord.UNBINDING.equals(row.status())) {
            throw MemberFailures.conflict("当前状态不可解绑");
        }
        members.markUnbound(row.memberId());
        accounts.updateBindStatus(row.accountId(), AccountBindingView.BIND_NONE);
        audit.record("binding", bindingId, row.deptId(), force ? "FORCE_UNBIND" : "UNBIND", reason,
                access.requireActorId());
        if (force) {
            outbox.forceUnbound(bindingId, row.accountId(), row.memberId(), row.deptId(), requestId, idemKey);
        } else {
            outbox.forceUnbound(bindingId, row.accountId(), row.memberId(), row.deptId(), requestId, idemKey);
        }
        bindings.deleteById(bindingId);
        log.info("binding unbound bindingId={} accountId={} force={}", bindingId, row.accountId(), force);
    }

    private MemberRecord insertMember(HostRecord host, CreateMemberCommand command, String actor) {
        if (command.subscribedMonths() < 1 || command.subscribedMonths() > 120) {
            throw MemberFailures.badRequest("subscribedMonths 必填且范围 1-120");
        }
        if (command.subscribedDays() < 0 || command.subscribedDays() > 36500) {
            throw MemberFailures.badRequest("subscribedDays 必填且范围 0-36500");
        }
        LocalDateTime expireAt = command.expireAt();
        if (expireAt == null) {
            throw MemberFailures.badRequest("expireAt 必填");
        }
        String douyin = DouyinIds.normalize(command.douyinId());
        LocalDateTime startAt = ExpireTimes.startAt(expireAt, command.subscribedDays());
        var computed = recalc.recalc(command.subscribedDays(), 0, startAt, expireAt, now());
        DuplicateKeyException last = null;
        for (int i = 0; i < CODE_RETRY; i++) {
            String memberCode = codes.next(host.hostCode());
            MemberRecord draft = new MemberRecord(
                    0, memberCode, douyin, command.douyinId(), host.deptId(),
                    command.subscribedMonths(), command.subscribedDays(), expireAt, startAt,
                    computed.availableCount(), 0, computed.remainCount(), computed.status(),
                    0, null, null, command.remark(), 0, now());
            try {
                long id = members.insert(draft, actor);
                return members.findById(id).orElseThrow(MemberFailures::notFound);
            } catch (DuplicateKeyException ex) {
                last = ex;
                String msg = Optional.ofNullable(ex.getMessage()).orElse("");
                if (msg.contains("uk_kjx_member_dept_douyin")) {
                    throw MemberFailures.conflict("该抖音号已录入");
                }
            }
        }
        throw last == null ? MemberFailures.conflict("会员编码生成冲突") : MemberFailures.conflict("会员编码生成冲突");
    }

    private HostRecord resolveHost(String hostCode, String compactMemberCode) {
        String explicit = MemberCodes.optionalHostCode(hostCode);
        List<HostRecord> inferred = hosts.findByCompactPrefix(compactMemberCode);
        if (explicit != null) {
            HostRecord host = hosts.findByHostCode(explicit).orElseThrow(MemberFailures::mismatch);
            if (inferred.stream().noneMatch(item -> item.deptId() == host.deptId())
                    && !compactMemberCode.startsWith(host.hostCode())) {
                throw MemberFailures.mismatch();
            }
            return host;
        }
        if (inferred.size() == 1) {
            return inferred.get(0);
        }
        throw MemberFailures.mismatch();
    }

    private boolean isBindLocked(long accountId, AccountBindingView view) {
        if (view != null && view.bindLockedUntil() != null && !now().isAfter(view.bindLockedUntil())) {
            return true;
        }
        return attempts != null && attempts.locked(attempts.current(accountId));
    }

    private void persistMismatchOutsideTx(long accountId) {
        AccountBindingView fresh = accounts.find(accountId).orElse(null);
        int failCount = fresh == null ? 1 : fresh.bindFailCount() + 1;
        if (attempts != null) {
            failCount = attempts.increment(accountId);
        }
        LocalDateTime lockedUntil = failCount >= FAIL_LIMIT ? now().plus(lockTtl) : null;
        accounts.recordFailure(accountId, failCount, lockedUntil);
        log.info("binding mismatch accountId={} failCount={}", accountId, failCount);
    }

    private Long scopedDept(DeptAccessPort access, Long deptId) {
        if (!access.ops()) {
            long home = access.requireDeptId();
            if (deptId != null && deptId != home) {
                throw MemberFailures.forbidden();
            }
            return home;
        }
        if (deptId != null) {
            access.requireAccess(deptId);
        }
        return deptId;
    }

    @SuppressWarnings("unchecked")
    private <T> T runIdempotent(String scene, long refId, long actorId, String idemKey, Object payload,
                                java.util.function.Supplier<T> action) {
        if (idemKey == null || idemKey.isBlank()) {
            throw MemberFailures.badRequest("X-Idempotency-Key 必填");
        }
        T executed = tx.execute(status -> {
            try {
                java.util.concurrent.atomic.AtomicReference<T> first = new java.util.concurrent.atomic.AtomicReference<>();
                IdempotencyResult result = idempotency.execute(
                        IdempotencyCommand.of(scene, refId, idemKey, payload).withActor(actorId),
                        () -> {
                            T value = action.get();
                            first.set(value);
                            return IdempotencyResult.of("OK", json(replayToken(value)));
                        });
                if (first.get() != null) {
                    return first.get();
                }
                return (T) decodeReplay(result.resultBodyJson());
            } catch (IdempotencyConflictException ex) {
                throw MemberFailures.idempotencyConflict();
            }
        });
        if (executed == null) {
            throw new IllegalStateException("transaction returned null");
        }
        return executed;
    }

    private Object replayToken(Object value) {
        if (value instanceof MemberRecord member) {
            return Map.of("k", "member", "id", member.id());
        }
        if (value instanceof HostRecord host) {
            return Map.of("k", "host", "id", host.deptId());
        }
        return value;
    }

    private Object decodeReplay(String json) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = objectMapper.readValue(json, Map.class);
            Object kind = map.get("k");
            if ("member".equals(kind)) {
                long id = ((Number) map.get("id")).longValue();
                return members.findById(id).orElseThrow(MemberFailures::notFound);
            }
            if ("host".equals(kind)) {
                long id = ((Number) map.get("id")).longValue();
                return hosts.findByDeptId(id).orElseThrow(MemberFailures::notFound);
            }
            return map;
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("idempotent decode failed", ex);
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("json failed", ex);
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock.withZone(SHANGHAI));
    }

    private static void requireReason(String reason) {
        requireText(reason, "reason");
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw MemberFailures.badRequest(field + " 必填");
        }
    }

    private static byte[] ipBytes(String ip) {
        if (ip == null || ip.isBlank()) {
            return null;
        }
        try {
            return InetAddress.getByName(ip).getAddress();
        } catch (Exception ex) {
            return null;
        }
    }

    public record CreateHostCommand(
            String hostCode, String hostName, Long parentDeptId, Long adminUserId,
            String dyAccount, String logoUrl, String contact, String remark, String reason) {
    }

    public record UpdateHostCommand(
            String hostName, String dyAccount, String logoUrl, String contact, String remark, String reason) {
    }

    public record CreateMemberCommand(
            String douyinId, int subscribedMonths, int subscribedDays, LocalDateTime expireAt, String remark,
            String reason) {
    }

    public record UpdateMemberCommand(
            Integer subscribedMonths, Integer subscribedDays, LocalDateTime expireAt, String remark, String reason) {
    }

    public record SubmitBindingCommand(String hostCode, String douyinId, String memberCode, int claimMonths) {
    }

    public record SubmitAppealCommand(String appealType, String content, String contact, List<String> attachments) {
    }
}
