package com.koujunxin.account.web;

import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.koujunxin.account.application.AccountApplicationService;
import com.koujunxin.account.application.AccountResults;
import com.koujunxin.account.application.ClientContext;

@RestController
@RequestMapping("/app/account")
@ConditionalOnBean(AccountApplicationService.class)
public class AppAccountController {

    private final AccountApplicationService accounts;

    public AppAccountController(AccountApplicationService accounts) {
        this.accounts = accounts;
    }

    @GetMapping("/profile")
    public ApiBody<AccountResults.ProfileResult> profile(HttpServletRequest request, HttpServletResponse response) {
        String requestId = bind(request, response);
        return ApiBody.ok(accounts.profile(), requestId);
    }

    @PutMapping("/profile")
    public ApiBody<AccountResults.ProfileResult> updateProfile(
            @Valid @RequestBody AccountRequests.UpdateProfileRequest body,
            HttpServletRequest request,
            HttpServletResponse response) {
        String requestId = bind(request, response);
        return ApiBody.ok(accounts.updateProfile(body.nickname(), body.avatar()), requestId);
    }

    @PutMapping("/password")
    public ApiBody<Map<String, Object>> changePassword(
            @Valid @RequestBody AccountRequests.ChangePasswordRequest body,
            HttpServletRequest request,
            HttpServletResponse response) {
        String requestId = bind(request, response);
        accounts.changePassword(body.oldPassword(), body.newPassword(), context(request, requestId));
        return ApiBody.ok(Map.of(), requestId);
    }

    @GetMapping("/devices")
    public ApiBody<List<AccountResults.DeviceView>> devices(HttpServletRequest request, HttpServletResponse response) {
        String requestId = bind(request, response);
        return ApiBody.ok(accounts.listDevices(), requestId);
    }

    @DeleteMapping("/devices/{id}")
    public ApiBody<Map<String, Object>> deleteDevice(
            @PathVariable("id") long id,
            HttpServletRequest request,
            HttpServletResponse response) {
        String requestId = bind(request, response);
        accounts.deleteDevice(id, context(request, requestId));
        return ApiBody.ok(Map.of(), requestId);
    }

    @PostMapping("/cancel")
    public ApiBody<AccountResults.CancelResult> cancel(
            @Valid @RequestBody AccountRequests.CancelAccountRequest body,
            HttpServletRequest request,
            HttpServletResponse response) {
        String requestId = bind(request, response);
        return ApiBody.ok(accounts.cancel(body.code(), body.reason(), context(request, requestId)), requestId);
    }

    @PostMapping("/cancel/revoke")
    public ApiBody<Map<String, Object>> revokeCancel(HttpServletRequest request, HttpServletResponse response) {
        String requestId = bind(request, response);
        accounts.revokeCancel(context(request, requestId));
        return ApiBody.ok(Map.of(), requestId);
    }

    private static String bind(HttpServletRequest request, HttpServletResponse response) {
        String requestId = AccountWebSupport.requestId(request);
        response.setHeader(AccountWebSupport.REQUEST_ID, requestId);
        return requestId;
    }

    private static ClientContext context(HttpServletRequest request, String requestId) {
        return new ClientContext(
                request.getHeader(AccountWebSupport.DEVICE_ID),
                AccountWebSupport.clientIp(request),
                AccountWebSupport.platform(request),
                request.getHeader("User-Agent"),
                requestId,
                request.getHeader(AccountWebSupport.IDEMPOTENCY));
    }
}
