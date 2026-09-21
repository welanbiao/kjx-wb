package com.koujunxin.runtime.web;

import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.koujunxin.runtime.api.RuntimeAccountPort;
import com.koujunxin.runtime.api.dto.StateMutation;
import com.koujunxin.runtime.session.RuntimeApplicationService;

@RestController
public class AppSessionController {

    private final RuntimeApplicationService runtime;
    private final RuntimeAccountPort accounts;

    public AppSessionController(RuntimeApplicationService runtime, RuntimeAccountPort accounts) {
        this.runtime = runtime;
        this.accounts = accounts;
    }

    @PostMapping("/app/sessions")
    public ApiBody<Map<String, Object>> create(@RequestBody Map<String, String> body, HttpServletRequest request) {
        String workCode = required(body, "workCode");
        return ApiBody.ok(runtime.createSession(accounts.requireAccountId(), workCode), RuntimeWebSupport.requestId(request));
    }

    @GetMapping("/app/sessions")
    public ApiBody<List<Map<String, Object>>> list(@RequestParam String workCode, HttpServletRequest request) {
        return ApiBody.ok(runtime.listSessions(accounts.requireAccountId(), workCode), RuntimeWebSupport.requestId(request));
    }

    @GetMapping("/app/sessions/{id}")
    public ApiBody<Map<String, Object>> get(
            @PathVariable("id") String id,
            @RequestParam String workCode,
            HttpServletRequest request) {
        return ApiBody.ok(runtime.getSession(accounts.requireAccountId(), id, workCode), RuntimeWebSupport.requestId(request));
    }

    @PostMapping("/app/sessions/{id}/state")
    public ApiBody<Map<String, Object>> apply(
            @PathVariable("id") String id,
            @RequestParam String workCode,
            @RequestBody StateMutation mutation,
            HttpServletRequest request) {
        return ApiBody.ok(runtime.apply(accounts.requireAccountId(), id, workCode, mutation), RuntimeWebSupport.requestId(request));
    }

    @GetMapping("/app/sessions/{id}/stage")
    public ApiBody<Map<String, Object>> stage(
            @PathVariable("id") String id,
            @RequestParam String workCode,
            HttpServletRequest request) {
        return ApiBody.ok(runtime.stage(accounts.requireAccountId(), id, workCode), RuntimeWebSupport.requestId(request));
    }

    @GetMapping("/app/sessions/{id}/sandbox")
    public ApiBody<Map<String, Object>> sandbox(
            @PathVariable("id") String id,
            @RequestParam String workCode,
            HttpServletRequest request) {
        return ApiBody.ok(
                runtime.sandboxBootstrap(accounts.requireAccountId(), id, workCode),
                RuntimeWebSupport.requestId(request));
    }

    @DeleteMapping("/app/sessions/{id}")
    public ApiBody<Map<String, Object>> end(
            @PathVariable("id") String id,
            @RequestParam String workCode,
            HttpServletRequest request) {
        return ApiBody.ok(runtime.endSession(accounts.requireAccountId(), id, workCode), RuntimeWebSupport.requestId(request));
    }

    private static String required(Map<String, String> body, String key) {
        String value = body == null ? null : body.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(key + " is required");
        }
        return value;
    }
}
