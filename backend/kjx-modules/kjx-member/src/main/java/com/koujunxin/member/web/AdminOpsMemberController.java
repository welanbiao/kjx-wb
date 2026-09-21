package com.koujunxin.member.web;

import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.koujunxin.member.api.MemberFailures;
import com.koujunxin.member.api.port.DeptAccessPort;
import com.koujunxin.member.application.MemberApplicationService;
import com.koujunxin.member.application.MemberApplicationService.CreateHostCommand;
import com.koujunxin.member.application.MemberApplicationService.CreateMemberCommand;
import com.koujunxin.member.application.MemberApplicationService.UpdateHostCommand;
import com.koujunxin.member.domain.AppealRecord;
import com.koujunxin.member.domain.BindingRecord;
import com.koujunxin.member.domain.HostRecord;
import com.koujunxin.member.domain.MemberRecord;
import com.koujunxin.member.domain.PageResult;
import com.koujunxin.member.normalize.ExpireTimes;
import com.koujunxin.member.web.AdminSelfMemberController.UpdateMemberBody;

@RestController
@RequestMapping("/admin-api/kjx")
@ConditionalOnBean(MemberApplicationService.class)
public class AdminOpsMemberController {

    private final MemberApplicationService members;
    private final DeptAccessPort access;

    public AdminOpsMemberController(MemberApplicationService members, DeptAccessPort access) {
        this.members = members;
        this.access = access;
    }

    @GetMapping("/member/page")
    public ApiBody<Map<String, Object>> pageMembers(
            @RequestParam(required = false) Long deptId,
            @RequestParam(required = false) String douyinId,
            @RequestParam(required = false) String memberCode,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer bindState,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize,
            HttpServletRequest request) {
        requireOps();
        PageResult<MemberRecord> page = members.pageMembers(access, deptId, douyinId, memberCode, status, bindState,
                pageNo, pageSize);
        return ApiBody.ok(MemberViews.page(page.list().stream()
                .map(item -> MemberViews.member(item, MemberViews.hostCodeOf(item)))
                .toList(), page.total()), MemberWebSupport.requestId(request));
    }

    @PostMapping("/member")
    public ApiBody<Map<String, Object>> create(
            @RequestBody CreateOpsMemberBody body,
            @RequestHeader(value = MemberWebSupport.IDEMPOTENCY, required = false) String idem,
            HttpServletRequest request) {
        requireOps();
        if (body.deptId() == null) {
            throw MemberFailures.badRequest("deptId 必填");
        }
        MemberRecord created = members.createMember(access, body.deptId(), body.toCommand(), idem, true);
        return ApiBody.ok(MemberViews.created(created), MemberWebSupport.requestId(request));
    }

    @GetMapping("/member/{id}")
    public ApiBody<Map<String, Object>> get(@PathVariable long id, HttpServletRequest request) {
        requireOps();
        MemberRecord member = members.getMember(access, id);
        return ApiBody.ok(MemberViews.member(member, MemberViews.hostCodeOf(member)),
                MemberWebSupport.requestId(request));
    }

    @PutMapping("/member/{id}")
    public ApiBody<Map<String, Object>> update(
            @PathVariable long id,
            @RequestBody UpdateMemberBody body,
            @RequestHeader(value = MemberWebSupport.IDEMPOTENCY, required = false) String idem,
            HttpServletRequest request) {
        requireOps();
        MemberRecord updated = members.updateMember(access, id, body.toCommand(), idem);
        return ApiBody.ok(MemberViews.member(updated, MemberViews.hostCodeOf(updated)),
                MemberWebSupport.requestId(request));
    }

    @PostMapping("/member/{id}/recalc")
    public ApiBody<Map<String, Object>> recalc(
            @PathVariable long id,
            @RequestBody ReasonBody body,
            @RequestHeader(value = MemberWebSupport.IDEMPOTENCY, required = false) String idem,
            HttpServletRequest request) {
        requireOps();
        MemberRecord updated = members.recalcMember(access, id, body.reason(), idem);
        return ApiBody.ok(MemberViews.member(updated, MemberViews.hostCodeOf(updated)),
                MemberWebSupport.requestId(request));
    }

    @GetMapping("/binding/page")
    public ApiBody<Map<String, Object>> pageBindings(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long deptId,
            @RequestParam(required = false) String memberCode,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize,
            HttpServletRequest request) {
        requireOps();
        PageResult<BindingRecord> page = members.pageBindings(access, deptId, status, memberCode, pageNo, pageSize);
        return ApiBody.ok(MemberViews.page(page.list().stream()
                .map(item -> MemberViews.binding(item, item.memberCodeInput()))
                .toList(), page.total()), MemberWebSupport.requestId(request));
    }

    @PostMapping("/binding/{id}/approve")
    public ApiBody<Map<String, Object>> approve(
            @PathVariable long id,
            @RequestBody(required = false) ApproveBody body,
            @RequestHeader(value = MemberWebSupport.IDEMPOTENCY, required = false) String idem,
            HttpServletRequest request) {
        requireOps();
        String requestId = MemberWebSupport.requestId(request);
        members.approveBinding(access, id, body == null ? null : body.remark(), idem, requestId);
        return ApiBody.ok(Map.of(), requestId);
    }

    @PostMapping("/binding/{id}/reject")
    public ApiBody<Map<String, Object>> reject(
            @PathVariable long id,
            @RequestBody ReasonBody body,
            @RequestHeader(value = MemberWebSupport.IDEMPOTENCY, required = false) String idem,
            HttpServletRequest request) {
        requireOps();
        String requestId = MemberWebSupport.requestId(request);
        members.rejectBinding(access, id, body.reason(), idem, requestId);
        return ApiBody.ok(Map.of(), requestId);
    }

    @PostMapping("/binding/{id}/force-unbind")
    public ApiBody<Map<String, Object>> forceUnbind(
            @PathVariable long id,
            @RequestBody ReasonBody body,
            @RequestHeader(value = MemberWebSupport.IDEMPOTENCY, required = false) String idem,
            HttpServletRequest request) {
        requireOps();
        String requestId = MemberWebSupport.requestId(request);
        members.forceUnbind(access, id, body.reason(), idem, requestId);
        return ApiBody.ok(Map.of(), requestId);
    }

    @GetMapping("/appeal/page")
    public ApiBody<Map<String, Object>> pageAppeals(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize,
            HttpServletRequest request) {
        requireOps();
        PageResult<AppealRecord> page = members.pageAppeals(access, status, pageNo, pageSize);
        return ApiBody.ok(MemberViews.page(page.list().stream().map(MemberViews::appeal).toList(), page.total()),
                MemberWebSupport.requestId(request));
    }

    @PostMapping("/appeal/{id}/handle")
    public ApiBody<Map<String, Object>> handleAppeal(
            @PathVariable long id,
            @RequestBody HandleAppealBody body,
            @RequestHeader(value = MemberWebSupport.IDEMPOTENCY, required = false) String idem,
            HttpServletRequest request) {
        requireOps();
        String requestId = MemberWebSupport.requestId(request);
        members.handleAppeal(access, id, body.result(), body.handleNote(), idem, requestId);
        return ApiBody.ok(Map.of(), requestId);
    }

    @GetMapping("/host/page")
    public ApiBody<Map<String, Object>> pageHosts(
            @RequestParam(required = false) String hostCode,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize,
            HttpServletRequest request) {
        requireOps();
        PageResult<HostRecord> page = members.pageHosts(access, hostCode, pageNo, pageSize);
        return ApiBody.ok(MemberViews.page(page.list().stream()
                .map(item -> MemberViews.host(item, members.hostName(item)))
                .toList(), page.total()), MemberWebSupport.requestId(request));
    }

    @PostMapping("/host")
    public ApiBody<Map<String, Object>> createHost(
            @RequestBody CreateHostBody body,
            @RequestHeader(value = MemberWebSupport.IDEMPOTENCY, required = false) String idem,
            HttpServletRequest request) {
        requireOps();
        String requestId = MemberWebSupport.requestId(request);
        HostRecord created = members.createHost(access, body.toCommand(), idem, requestId);
        return ApiBody.ok(MemberViews.host(created, members.hostName(created)), requestId);
    }

    @GetMapping("/host/{id}")
    public ApiBody<Map<String, Object>> getHost(@PathVariable long id, HttpServletRequest request) {
        requireOps();
        HostRecord host = members.getHost(access, id);
        return ApiBody.ok(MemberViews.host(host, members.hostName(host)), MemberWebSupport.requestId(request));
    }

    @PutMapping("/host/{id}")
    public ApiBody<Map<String, Object>> updateHost(
            @PathVariable long id,
            @RequestBody UpdateHostBody body,
            @RequestHeader(value = MemberWebSupport.IDEMPOTENCY, required = false) String idem,
            HttpServletRequest request) {
        requireOps();
        HostRecord updated = members.updateHost(access, id, body.toCommand(), idem);
        return ApiBody.ok(MemberViews.host(updated, members.hostName(updated)), MemberWebSupport.requestId(request));
    }

    private void requireOps() {
        if (!access.ops()) {
            throw MemberFailures.forbidden();
        }
    }

    public record CreateOpsMemberBody(
            String douyinId, Integer subscribedMonths, Integer subscribedDays, String expireAt, String remark,
            Long deptId, String reason) {
        CreateMemberCommand toCommand() {
            if (subscribedMonths == null || subscribedDays == null || expireAt == null) {
                throw MemberFailures.badRequest("月份、天数、到期时间均必填");
            }
            return new CreateMemberCommand(douyinId, subscribedMonths, subscribedDays,
                    ExpireTimes.parseRequired(expireAt), remark, reason);
        }
    }

    public record ReasonBody(String reason) {
    }

    public record ApproveBody(String remark) {
    }

    public record HandleAppealBody(String result, String handleNote) {
    }

    public record CreateHostBody(
            String hostCode, String hostName, Long parentDeptId, Long adminUserId,
            String dyAccount, String logoUrl, String contact, String remark, String reason) {
        CreateHostCommand toCommand() {
            return new CreateHostCommand(hostCode, hostName, parentDeptId, adminUserId, dyAccount, logoUrl, contact,
                    remark, reason);
        }
    }

    public record UpdateHostBody(
            String hostName, String dyAccount, String logoUrl, String contact, String remark, String reason) {
        UpdateHostCommand toCommand() {
            return new UpdateHostCommand(hostName, dyAccount, logoUrl, contact, remark, reason);
        }
    }
}
