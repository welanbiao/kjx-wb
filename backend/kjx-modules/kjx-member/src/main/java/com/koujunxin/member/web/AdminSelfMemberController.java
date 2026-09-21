package com.koujunxin.member.web;

import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.koujunxin.member.api.port.DeptAccessPort;
import com.koujunxin.member.api.MemberFailures;
import com.koujunxin.member.application.MemberApplicationService;
import com.koujunxin.member.application.MemberApplicationService.CreateMemberCommand;
import com.koujunxin.member.application.MemberApplicationService.UpdateMemberCommand;
import com.koujunxin.member.domain.BindingRecord;
import com.koujunxin.member.domain.MemberRecord;
import com.koujunxin.member.domain.PageResult;
import com.koujunxin.member.normalize.ExpireTimes;

@RestController
@RequestMapping("/admin-api/kjx/self")
@ConditionalOnBean(MemberApplicationService.class)
public class AdminSelfMemberController {

    private final MemberApplicationService members;
    private final DeptAccessPort access;

    public AdminSelfMemberController(MemberApplicationService members, DeptAccessPort access) {
        this.members = members;
        this.access = access;
    }

    @GetMapping("/host")
    public ApiBody<Map<String, Object>> host(HttpServletRequest request) {
        requireHost();
        var host = members.selfHost(access);
        return ApiBody.ok(MemberViews.host(host, members.hostName(host)), MemberWebSupport.requestId(request));
    }

    @GetMapping("/member/page")
    public ApiBody<Map<String, Object>> pageMembers(
            @RequestParam(required = false) String douyinId,
            @RequestParam(required = false) String memberCode,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer bindState,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize,
            HttpServletRequest request) {
        requireHost();
        PageResult<MemberRecord> page = members.pageMembers(access, null, douyinId, memberCode, status, bindState,
                pageNo, pageSize);
        return ApiBody.ok(MemberViews.page(page.list().stream()
                .map(item -> MemberViews.member(item, MemberViews.hostCodeOf(item)))
                .toList(), page.total()), MemberWebSupport.requestId(request));
    }

    @PostMapping("/member")
    public ApiBody<Map<String, Object>> create(
            @RequestBody CreateSelfMemberBody body,
            @RequestHeader(value = MemberWebSupport.IDEMPOTENCY, required = false) String idem,
            HttpServletRequest request) {
        requireHost();
        MemberRecord created = members.createMember(access, access.requireDeptId(), body.toCommand(), idem, false);
        return ApiBody.ok(MemberViews.created(created), MemberWebSupport.requestId(request));
    }

    @PutMapping("/member/{id}")
    public ApiBody<Map<String, Object>> update(
            @PathVariable long id,
            @RequestBody UpdateMemberBody body,
            @RequestHeader(value = MemberWebSupport.IDEMPOTENCY, required = false) String idem,
            HttpServletRequest request) {
        requireHost();
        MemberRecord updated = members.updateMember(access, id, body.toCommand(), idem);
        return ApiBody.ok(MemberViews.member(updated, MemberViews.hostCodeOf(updated)),
                MemberWebSupport.requestId(request));
    }

    @GetMapping("/member/{id}/code")
    public ApiBody<Map<String, Object>> code(@PathVariable long id, HttpServletRequest request) {
        requireHost();
        return ApiBody.ok(Map.of("memberCode", members.getMemberCode(access, id)),
                MemberWebSupport.requestId(request));
    }

    @GetMapping("/binding/page")
    public ApiBody<Map<String, Object>> pageBindings(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String memberCode,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize,
            HttpServletRequest request) {
        requireHost();
        PageResult<BindingRecord> page = members.pageBindings(access, null, status, memberCode, pageNo, pageSize);
        return ApiBody.ok(MemberViews.page(page.list().stream()
                .map(item -> MemberViews.binding(item, item.memberCodeInput()))
                .toList(), page.total()), MemberWebSupport.requestId(request));
    }

    @GetMapping("/member/export")
    public ResponseEntity<byte[]> export(
            @RequestParam(required = false) String status,
            HttpServletRequest request) {
        requireHost();
        byte[] body = MemberXlsxExporter.export(members.exportMembers(access, null, status));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=members.xlsx")
                .header(MemberWebSupport.REQUEST_ID, MemberWebSupport.requestId(request))
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(body);
    }

    private void requireHost() {
        if (!access.host()) {
            throw MemberFailures.forbidden();
        }
    }

    public record CreateSelfMemberBody(
            String douyinId, Integer subscribedMonths, Integer subscribedDays, String expireAt, String remark) {
        CreateMemberCommand toCommand() {
            if (subscribedMonths == null || subscribedDays == null || expireAt == null) {
                throw com.koujunxin.member.api.MemberFailures.badRequest("月份、天数、到期时间均必填");
            }
            return new CreateMemberCommand(douyinId, subscribedMonths, subscribedDays,
                    ExpireTimes.parseRequired(expireAt), remark, null);
        }
    }

    public record UpdateMemberBody(
            Integer subscribedMonths, Integer subscribedDays, String expireAt, String remark, String reason) {
        UpdateMemberCommand toCommand() {
            return new UpdateMemberCommand(
                    subscribedMonths,
                    subscribedDays,
                    expireAt == null || expireAt.isBlank() ? null : ExpireTimes.parseRequired(expireAt),
                    remark,
                    reason);
        }
    }
}
