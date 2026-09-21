package com.koujunxin.work.web;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.koujunxin.work.catalog.WorkType;
import com.koujunxin.work.query.PublicWorkQueryService;

@RestController
@RequestMapping("/app/works")
@ConditionalOnBean(PublicWorkQueryService.class)
public class AppWorkController {

    private final PublicWorkQueryService queryService;
    private final AppAccountAccessor accounts;

    public AppWorkController(PublicWorkQueryService queryService, AppAccountAccessor accounts) {
        this.queryService = queryService;
        this.accounts = accounts;
    }

    @GetMapping
    public ApiEnvelope<WorkApiMapper.WorkPageBody> list(
            @RequestParam(required = false) String workType,
            @RequestParam(required = false) Boolean unlocked,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId) {
        long accountId = accounts.requireAccountId();
        WorkType type = workType == null || workType.isBlank() ? null : WorkType.from(workType);
        return ApiEnvelope.ok(requestId, WorkApiMapper.toPage(
                queryService.list(accountId, type, unlocked, pageNo, pageSize)));
    }

    @GetMapping("/{workCode}")
    public ApiEnvelope<WorkApiMapper.WorkDetailBody> detail(
            @PathVariable String workCode,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId) {
        long accountId = accounts.requireAccountId();
        return ApiEnvelope.ok(requestId, WorkApiMapper.toDetail(queryService.getDetail(accountId, workCode)));
    }
}
