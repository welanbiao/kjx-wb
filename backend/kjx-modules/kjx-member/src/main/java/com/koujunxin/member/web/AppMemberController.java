package com.koujunxin.member.web;

import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.koujunxin.member.api.port.AppAccountAccessor;
import com.koujunxin.member.application.MemberApplicationService;
import com.koujunxin.member.application.MemberApplicationService.SubmitAppealCommand;
import com.koujunxin.member.application.MemberApplicationService.SubmitBindingCommand;
import com.koujunxin.member.domain.HostRecord;

@RestController
@RequestMapping("/app/member")
@ConditionalOnBean(MemberApplicationService.class)
public class AppMemberController {

    private final MemberApplicationService members;
    private final AppAccountAccessor accounts;

    public AppMemberController(MemberApplicationService members, AppAccountAccessor accounts) {
        this.members = members;
        this.accounts = accounts;
    }

    @GetMapping("/binding")
    public ApiBody<Map<String, Object>> getBinding(HttpServletRequest request) {
        return ApiBody.ok(members.getBinding(accounts.requireAccountId()), MemberWebSupport.requestId(request));
    }

    @PostMapping("/binding")
    public ApiBody<Map<String, Object>> submitBinding(
            @RequestBody SubmitBindingCommand body,
            @RequestHeader(value = MemberWebSupport.IDEMPOTENCY, required = false) String idem,
            HttpServletRequest request) {
        String requestId = MemberWebSupport.requestId(request);
        return ApiBody.ok(members.submitBinding(
                accounts.requireAccountId(),
                body,
                idem,
                requestId,
                MemberWebSupport.clientIp(request),
                request.getHeader(MemberWebSupport.DEVICE_ID)), requestId);
    }

    @GetMapping("/hosts")
    public ApiBody<List<Map<String, Object>>> hosts(HttpServletRequest request) {
        List<Map<String, Object>> data = members.listHosts().stream()
                .map(host -> MemberViews.hostOption(host, members.hostName(host)))
                .toList();
        return ApiBody.ok(data, MemberWebSupport.requestId(request));
    }

    @GetMapping("/profile")
    public ApiBody<Map<String, Object>> profile(HttpServletRequest request) {
        return ApiBody.ok(members.memberProfile(accounts.requireAccountId()), MemberWebSupport.requestId(request));
    }

    @PostMapping("/binding/appeal")
    public ApiBody<Map<String, Object>> appeal(
            @RequestBody SubmitAppealCommand body,
            @RequestHeader(value = MemberWebSupport.IDEMPOTENCY, required = false) String idem,
            HttpServletRequest request) {
        return ApiBody.ok(members.submitAppeal(accounts.requireAccountId(), body, idem),
                MemberWebSupport.requestId(request));
    }
}
