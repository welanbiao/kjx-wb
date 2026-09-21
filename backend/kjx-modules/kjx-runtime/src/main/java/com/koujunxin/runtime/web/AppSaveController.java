package com.koujunxin.runtime.web;

import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.koujunxin.runtime.api.RuntimeAccountPort;
import com.koujunxin.runtime.session.RuntimeApplicationService;

@RestController
public class AppSaveController {

    private final RuntimeApplicationService runtime;
    private final RuntimeAccountPort accounts;

    public AppSaveController(RuntimeApplicationService runtime, RuntimeAccountPort accounts) {
        this.runtime = runtime;
        this.accounts = accounts;
    }

    @GetMapping("/app/saves")
    public ApiBody<List<Map<String, Object>>> list(
            @RequestParam String sessionId,
            @RequestParam String workCode,
            HttpServletRequest request) {
        return ApiBody.ok(
                runtime.listSaves(accounts.requireAccountId(), sessionId, workCode),
                RuntimeWebSupport.requestId(request));
    }

    @PostMapping("/app/saves")
    public ApiBody<Map<String, Object>> save(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        String sessionId = string(body, "sessionId");
        String workCode = string(body, "workCode");
        String slot = body.get("slot") == null ? "auto" : String.valueOf(body.get("slot"));
        Integer version = body.get("version") instanceof Number number ? number.intValue() : null;
        return ApiBody.ok(
                runtime.save(accounts.requireAccountId(), sessionId, workCode, slot, version),
                RuntimeWebSupport.requestId(request));
    }

    private static String string(Map<String, Object> body, String key) {
        Object value = body == null ? null : body.get(key);
        if (value == null || String.valueOf(value).isBlank()) {
            throw new IllegalArgumentException(key + " is required");
        }
        return String.valueOf(value);
    }
}
