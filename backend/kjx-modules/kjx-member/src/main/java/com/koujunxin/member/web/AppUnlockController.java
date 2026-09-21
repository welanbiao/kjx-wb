package com.koujunxin.member.web;

import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.koujunxin.member.api.port.AppAccountAccessor;
import com.koujunxin.member.domain.PageResult;
import com.koujunxin.member.unlock.UnlockApplicationService;
import com.koujunxin.member.unlock.UnlockOutcome;

@RestController
@ConditionalOnBean(UnlockApplicationService.class)
public class AppUnlockController {

    private final UnlockApplicationService unlocks;
    private final AppAccountAccessor accounts;

    public AppUnlockController(UnlockApplicationService unlocks, AppAccountAccessor accounts) {
        this.unlocks = unlocks;
        this.accounts = accounts;
    }

    @PostMapping("/app/works/{workCode}/unlock")
    public ApiBody<Map<String, Object>> unlock(
            @PathVariable String workCode,
            @RequestHeader(value = MemberWebSupport.IDEMPOTENCY, required = false) String idem,
            HttpServletRequest request) {
        String requestId = MemberWebSupport.requestId(request);
        UnlockOutcome outcome = unlocks.unlock(
                accounts.requireAccountId(),
                workCode,
                idem,
                requestId,
                MemberWebSupport.clientIp(request),
                request.getHeader(MemberWebSupport.DEVICE_ID));
        return ApiBody.of(outcome.code(), outcome.data(), outcome.message(), requestId);
    }

    @GetMapping("/app/entitlements")
    public ApiBody<Map<String, Object>> list(
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize,
            HttpServletRequest request) {
        PageResult<Map<String, Object>> page = unlocks.listMine(accounts.requireAccountId(), pageNo, pageSize);
        return ApiBody.ok(MemberViews.page(page.list(), page.total()), MemberWebSupport.requestId(request));
    }
}
