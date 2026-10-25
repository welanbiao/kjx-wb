package com.koujunxin.runtime.web;

import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.koujunxin.runtime.api.RuntimeAccountPort;
import com.koujunxin.runtime.dreamphone.DreamPhoneRuntimeService;

@RestController
public class AppDreamPhoneController {

    private final DreamPhoneRuntimeService phone;
    private final RuntimeAccountPort accounts;

    public AppDreamPhoneController(DreamPhoneRuntimeService phone, RuntimeAccountPort accounts) {
        this.phone = phone;
        this.accounts = accounts;
    }

    @GetMapping("/app/sessions/{id}/dream-phone")
    public ApiBody<Map<String, Object>> view(@PathVariable("id") String id, HttpServletRequest request) {
        return ApiBody.ok(phone.view(accounts.requireAccountId(), id), RuntimeWebSupport.requestId(request));
    }

    @PostMapping("/app/sessions/{id}/dream-phone/actions")
    public ApiBody<Map<String, Object>> action(
            @PathVariable("id") String id,
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {
        return ApiBody.ok(phone.action(accounts.requireAccountId(), id, body), RuntimeWebSupport.requestId(request));
    }
}
