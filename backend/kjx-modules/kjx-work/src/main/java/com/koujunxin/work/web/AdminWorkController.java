package com.koujunxin.work.web;

import java.util.LinkedHashMap;
import java.util.Map;

import org.dromara.common.satoken.utils.StpKit;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.koujunxin.common.idempotent.IdempotencyCommand;
import com.koujunxin.common.idempotent.IdempotencyResult;
import com.koujunxin.common.idempotent.IdempotencyService;
import com.koujunxin.work.catalog.AccessForm;
import com.koujunxin.work.catalog.AdminWorkQuery;
import com.koujunxin.work.catalog.CreateWorkCommand;
import com.koujunxin.work.catalog.UpdateWorkCommand;
import com.koujunxin.work.catalog.WorkCatalogService;
import com.koujunxin.work.catalog.WorkRecord;
import com.koujunxin.work.catalog.WorkStatus;
import com.koujunxin.work.catalog.WorkType;

@RestController
@RequestMapping("/admin-api/kjx/work")
@ConditionalOnBean(WorkCatalogService.class)
public class AdminWorkController {

    private final WorkCatalogService catalog;
    private final ObjectProvider<IdempotencyService> idempotency;
    private final ObjectMapper objectMapper;

    public AdminWorkController(
            WorkCatalogService catalog,
            ObjectProvider<IdempotencyService> idempotency,
            ObjectMapper objectMapper) {
        this.catalog = catalog;
        this.idempotency = idempotency;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/page")
    public ApiEnvelope<WorkApiMapper.AdminWorkPageBody> page(
            @RequestParam(required = false) String workType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId) {
        AdminWorkQuery query = new AdminWorkQuery(
                workType == null || workType.isBlank() ? null : WorkType.from(workType),
                status == null || status.isBlank() ? null : WorkStatus.from(status),
                keyword, pageNo, pageSize);
        WorkCatalogService.PageData<WorkRecord> page = catalog.pageAdmin(query);
        return ApiEnvelope.ok(requestId, new WorkApiMapper.AdminWorkPageBody(
                page.list().stream().map(WorkApiMapper::toAdmin).toList(), page.total()));
    }

    @PostMapping
    public ApiEnvelope<WorkApiMapper.AdminWorkBody> create(
            @RequestBody CreateWorkRequestBody body,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId) {
        CreateWorkCommand command = new CreateWorkCommand(
                body.workCode(),
                body.name(),
                WorkType.from(body.workType()),
                body.summary(),
                body.coverUrl(),
                null,
                body.category(),
                body.tags(),
                body.accessForm() == null ? AccessForm.A : AccessForm.from(body.accessForm()),
                body.grayRatio(),
                body.sortWeight(),
                body.reason(),
                actor());
        WorkRecord created = executeIdempotent("work_create", hashRef(body.workCode()), idempotencyKey, command,
                () -> catalog.create(command));
        return ApiEnvelope.ok(requestId, WorkApiMapper.toAdmin(created));
    }

    @GetMapping("/{id}")
    public ApiEnvelope<WorkApiMapper.AdminWorkBody> get(
            @PathVariable long id,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId) {
        return ApiEnvelope.ok(requestId, WorkApiMapper.toAdmin(catalog.getById(id)));
    }

    @PutMapping("/{id}")
    public ApiEnvelope<WorkApiMapper.AdminWorkBody> update(
            @PathVariable long id,
            @RequestBody UpdateWorkRequestBody body,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId) {
        UpdateWorkCommand command = new UpdateWorkCommand(
                id,
                body.name(),
                body.summary(),
                body.coverUrl(),
                null,
                body.category(),
                body.tags(),
                body.status() == null || body.status().isBlank() ? null : WorkStatus.from(body.status()),
                body.accessForm() == null || body.accessForm().isBlank() ? null : AccessForm.from(body.accessForm()),
                body.grayRatio(),
                body.sortWeight(),
                null,
                body.reason(),
                actor());
        WorkRecord updated = executeIdempotent("work_update", id, idempotencyKey, command,
                () -> catalog.update(command));
        return ApiEnvelope.ok(requestId, WorkApiMapper.toAdmin(updated));
    }

    private WorkRecord executeIdempotent(
            String scene, long refId, String key, Object payload, java.util.function.Supplier<WorkRecord> action) {
        IdempotencyService service = idempotency.getIfAvailable();
        if (service == null || key == null || key.isBlank()) {
            return action.get();
        }
        WorkRecord[] holder = new WorkRecord[1];
        IdempotencyResult result = service.execute(IdempotencyCommand.of(scene, refId, key, payload), () -> {
            holder[0] = action.get();
            try {
                return IdempotencyResult.of("OK", objectMapper.writeValueAsString(Map.of("id", holder[0].id())));
            } catch (JsonProcessingException ex) {
                return IdempotencyResult.of("OK", "{\"id\":" + holder[0].id() + "}");
            }
        });
        if (holder[0] != null) {
            return holder[0];
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = objectMapper.readValue(result.resultBodyJson(), LinkedHashMap.class);
            Object id = map.get("id");
            if (id instanceof Number number) {
                return catalog.getById(number.longValue());
            }
        } catch (Exception ex) {
            throw new IllegalStateException("idempotent replay missing work id", ex);
        }
        throw new IllegalStateException("idempotent replay missing work id");
    }

    private static long hashRef(String workCode) {
        if (workCode == null) {
            return 1L;
        }
        int hashed = Math.abs(workCode.hashCode());
        return hashed == 0 ? 1L : hashed;
    }

    private static String actor() {
        try {
            return String.valueOf(StpKit.ADMIN.getLoginId());
        } catch (Exception ex) {
            return "admin";
        }
    }
}
