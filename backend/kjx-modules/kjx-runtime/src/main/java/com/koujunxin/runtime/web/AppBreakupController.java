package com.koujunxin.runtime.web;

import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.koujunxin.runtime.api.RuntimeAccountPort;
import com.koujunxin.runtime.breakup.BreakupAssetService;
import com.koujunxin.runtime.breakup.BreakupRuntimeService;
import com.koujunxin.runtime.breakup.BreakupWorks;

@RestController
public class AppBreakupController {

    private final BreakupRuntimeService breakup;
    private final RuntimeAccountPort accounts;

    public AppBreakupController(BreakupRuntimeService breakup, RuntimeAccountPort accounts) {
        this.breakup = breakup;
        this.accounts = accounts;
    }

    @GetMapping("/app/sessions/{id}/breakup")
    public ApiBody<Map<String, Object>> view(@PathVariable("id") String id, HttpServletRequest request) {
        return ApiBody.ok(breakup.view(accounts.requireAccountId(), id), RuntimeWebSupport.requestId(request));
    }

    @PostMapping("/app/sessions/{id}/breakup/actions")
    public ApiBody<Map<String, Object>> action(
            @PathVariable("id") String id,
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {
        return ApiBody.ok(breakup.action(accounts.requireAccountId(), id, body), RuntimeWebSupport.requestId(request));
    }

    @GetMapping("/app/works/" + BreakupWorks.CODE + "/assets")
    public void asset(
            @RequestParam String key,
            @RequestParam long e,
            @RequestParam String s,
            HttpServletResponse response) throws Exception {
        byte[] body = breakup.asset(key, e, s);
        response.setHeader(HttpHeaders.CACHE_CONTROL, "private, max-age=60");
        response.setContentType(BreakupAssetService.contentType(key));
        response.setContentLength(body.length);
        response.getOutputStream().write(body);
    }
}
