package com.koujunxin.runtime.web;

import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.koujunxin.runtime.api.RuntimeAccountPort;
import com.koujunxin.runtime.session.RuntimeApplicationService;

@RestController
@ConditionalOnBean(RuntimeApplicationService.class)
public class AppHostProxyController {

    private final RuntimeApplicationService runtime;
    private final RuntimeAccountPort accounts;

    public AppHostProxyController(RuntimeApplicationService runtime, RuntimeAccountPort accounts) {
        this.runtime = runtime;
        this.accounts = accounts;
    }

    @PostMapping("/app/runtime/host")
    public ApiBody<Map<String, Object>> host(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        long accountId = accounts.requireAccountId();
        return ApiBody.ok(
                runtime.hostInvoke(
                        accountId,
                        required(body, "sessionId"),
                        required(body, "workCode"),
                        required(body, "nonce"),
                        stringOr(body, "iframeOrigin", "null"),
                        required(body, "method"),
                        payloadJson(body)),
                RuntimeWebSupport.requestId(request));
    }

    @PostMapping("/app/tools/invoke")
    public ApiBody<Map<String, Object>> tools(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        long accountId = accounts.requireAccountId();
        return ApiBody.ok(
                runtime.invokeToolHttp(
                        accountId,
                        required(body, "sessionId"),
                        required(body, "workCode"),
                        stringOr(body, "nonce", null),
                        stringOr(body, "iframeOrigin", "null"),
                        payloadJson(body)),
                RuntimeWebSupport.requestId(request));
    }

    private static String required(Map<String, Object> body, String key) {
        String value = stringOr(body, key, null);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(key + " is required");
        }
        return value;
    }

    private static String stringOr(Map<String, Object> body, String key, String fallback) {
        if (body == null || body.get(key) == null) {
            return fallback;
        }
        return String.valueOf(body.get(key));
    }

    private static String payloadJson(Map<String, Object> body) {
        if (body == null || body.get("payloadJson") == null) {
            return "{}";
        }
        Object raw = body.get("payloadJson");
        return raw instanceof String text ? text : String.valueOf(raw);
    }
}
