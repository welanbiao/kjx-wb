package com.koujunxin.account.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.koujunxin.account.application.AccountApplicationService;
import com.koujunxin.account.application.AccountResults;
import com.koujunxin.account.application.ClientContext;

@RestController
@RequestMapping("/app/auth")
@ConditionalOnBean(AccountApplicationService.class)
public class AppAuthController {

    private final AccountApplicationService accounts;

    public AppAuthController(AccountApplicationService accounts) {
        this.accounts = accounts;
    }

    @PostMapping("/send-code")
    public ApiBody<AccountResults.SendCodeResult> sendCode(
            @Valid @RequestBody AccountRequests.SendCodeRequest body,
            HttpServletRequest request,
            HttpServletResponse response) {
        String requestId = bindRequestId(request, response);
        AccountResults.SendCodeResult data = accounts.sendCode(
                body.phone(), body.scene(), body.captchaId(), body.captchaData(), context(request, requestId, true));
        return ApiBody.ok(data, requestId);
    }

    @PostMapping("/register")
    public ApiBody<AccountResults.RegisterResult> register(
            @Valid @RequestBody AccountRequests.RegisterRequest body,
            HttpServletRequest request,
            HttpServletResponse response) {
        String requestId = bindRequestId(request, response);
        AccountResults.RegisterResult data = accounts.register(
                body.phone(), body.password(), body.code(), body.agreeTerms(), context(request, requestId, true));
        return ApiBody.ok(data, requestId);
    }

    @PostMapping("/login")
    public ApiBody<AccountResults.LoginResult> login(
            @Valid @RequestBody AccountRequests.LoginRequest body,
            HttpServletRequest request,
            HttpServletResponse response) {
        String requestId = bindRequestId(request, response);
        AccountResults.LoginResult data = accounts.loginByPassword(
                body.phone(), body.password(), body.captchaId(), body.captchaData(), context(request, requestId, false));
        return ApiBody.ok(data, requestId);
    }

    @PostMapping("/login-by-code")
    public ApiBody<AccountResults.LoginResult> loginByCode(
            @Valid @RequestBody AccountRequests.LoginByCodeRequest body,
            HttpServletRequest request,
            HttpServletResponse response) {
        String requestId = bindRequestId(request, response);
        AccountResults.LoginResult data = accounts.loginByCode(
                body.phone(), body.code(), context(request, requestId, false));
        return ApiBody.ok(data, requestId);
    }

    @PostMapping("/logout")
    public ApiBody<Object> logout(HttpServletRequest request, HttpServletResponse response) {
        String requestId = bindRequestId(request, response);
        accounts.logout(context(request, requestId, false));
        return ApiBody.ok(MapOfEmpty.empty(), requestId);
    }

    @PostMapping("/refresh")
    public ApiBody<AccountResults.LoginResult> refresh(HttpServletRequest request, HttpServletResponse response) {
        String requestId = bindRequestId(request, response);
        return ApiBody.ok(accounts.refresh(context(request, requestId, false)), requestId);
    }

    @PostMapping("/reset-password")
    public ApiBody<Object> resetPassword(
            @Valid @RequestBody AccountRequests.ResetPasswordRequest body,
            HttpServletRequest request,
            HttpServletResponse response) {
        String requestId = bindRequestId(request, response);
        accounts.resetPassword(body.phone(), body.code(), body.newPassword(), context(request, requestId, true));
        return ApiBody.ok(MapOfEmpty.empty(), requestId);
    }

    private static String bindRequestId(HttpServletRequest request, HttpServletResponse response) {
        String requestId = AccountWebSupport.requestId(request);
        response.setHeader(AccountWebSupport.REQUEST_ID, requestId);
        return requestId;
    }

    private static ClientContext context(HttpServletRequest request, String requestId, boolean requireIdem) {
        String key = request.getHeader(AccountWebSupport.IDEMPOTENCY);
        return new ClientContext(
                request.getHeader(AccountWebSupport.DEVICE_ID),
                AccountWebSupport.clientIp(request),
                AccountWebSupport.platform(request),
                request.getHeader("User-Agent"),
                requestId,
                key);
    }

    static final class MapOfEmpty {
        private MapOfEmpty() {
        }

        static java.util.Map<String, Object> empty() {
            return java.util.Map.of();
        }
    }
}
