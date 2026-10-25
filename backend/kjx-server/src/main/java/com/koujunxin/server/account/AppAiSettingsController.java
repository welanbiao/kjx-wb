package com.koujunxin.server.account;

import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.koujunxin.account.api.AccountFailures;
import com.koujunxin.account.web.AccountWebSupport;
import com.koujunxin.account.web.ApiBody;
import com.koujunxin.runtime.api.RuntimeAccountPort;
import com.koujunxin.runtime.api.port.AccountAiSettingsPort;

@RestController
public class AppAiSettingsController {

    private final AccountAiSettingsPort settings;
    private final RuntimeAccountPort accounts;

    public AppAiSettingsController(AccountAiSettingsPort settings, RuntimeAccountPort accounts) {
        this.settings = settings;
        this.accounts = accounts;
    }

    @GetMapping("/app/account/ai-settings")
    public ApiBody<Map<String, Object>> get(HttpServletRequest request, HttpServletResponse response) {
        String requestId = bind(request, response);
        long accountId = accounts.requireAccountId();
        return ApiBody.ok(view(settings.find(accountId).orElse(null)), requestId);
    }

    @PostMapping("/app/account/ai-settings")
    public ApiBody<Map<String, Object>> save(
            @RequestBody AiSettingsRequest body,
            HttpServletRequest request,
            HttpServletResponse response) {
        String requestId = bind(request, response);
        long accountId = accounts.requireAccountId();
        if (body == null) {
            throw AccountFailures.badRequest("参数错误");
        }
        try {
            settings.save(accountId, body.baseUrl(), body.apiKey(), body.model());
        } catch (IllegalArgumentException ex) {
            throw AccountFailures.badRequest("请填写 Base URL、API Key 与 Model");
        }
        return ApiBody.ok(view(settings.find(accountId).orElse(null)), requestId);
    }

    private static Map<String, Object> view(AccountAiSettingsPort.Snapshot snapshot) {
        Map<String, Object> data = new LinkedHashMap<>();
        boolean configured = snapshot != null;
        data.put("configured", configured);
        data.put("baseUrl", configured ? snapshot.baseUrl() : "");
        data.put("model", configured ? snapshot.model() : "");
        return data;
    }

    private static String bind(HttpServletRequest request, HttpServletResponse response) {
        String requestId = AccountWebSupport.requestId(request);
        response.setHeader(AccountWebSupport.REQUEST_ID, requestId);
        return requestId;
    }

    public record AiSettingsRequest(String baseUrl, String apiKey, String model) {

        @Override
        public String toString() {
            return "AiSettingsRequest[baseUrl=" + baseUrl + ", apiKey=redacted, model=" + model + "]";
        }
    }
}
