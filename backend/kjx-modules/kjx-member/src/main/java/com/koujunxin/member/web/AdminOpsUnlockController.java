package com.koujunxin.member.web;

import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.koujunxin.member.api.MemberFailures;
import com.koujunxin.member.api.port.DeptAccessPort;
import com.koujunxin.member.domain.MemberRecord;
import com.koujunxin.member.domain.PageResult;
import com.koujunxin.member.domain.UnlockRecordRow;
import com.koujunxin.member.unlock.UnlockApplicationService;
import com.koujunxin.work.api.WorkCatalogQueryPort;
import com.koujunxin.work.api.WorkSnapshot;

@RestController
@RequestMapping("/admin-api/kjx")
@ConditionalOnBean(UnlockApplicationService.class)
public class AdminOpsUnlockController {

    private final UnlockApplicationService unlocks;
    private final DeptAccessPort access;
    private final WorkCatalogQueryPort works;

    public AdminOpsUnlockController(
            UnlockApplicationService unlocks,
            DeptAccessPort access,
            WorkCatalogQueryPort works) {
        this.unlocks = unlocks;
        this.access = access;
        this.works = works;
    }

    @PostMapping("/member/{id}/entitlement")
    public ApiBody<Map<String, Object>> adjust(
            @PathVariable long id,
            @RequestBody AdjustBody body,
            @RequestHeader(value = MemberWebSupport.IDEMPOTENCY, required = false) String idem,
            HttpServletRequest request) {
        requireOps();
        String requestId = MemberWebSupport.requestId(request);
        MemberRecord updated = unlocks.adjust(access, id, body.action(), body.workCode(), body.reason(), idem, requestId);
        return ApiBody.ok(MemberViews.member(updated, MemberViews.hostCodeOf(updated)), requestId);
    }

    @GetMapping("/unlock-record/page")
    public ApiBody<Map<String, Object>> page(
            @RequestParam(required = false) Long deptId,
            @RequestParam(required = false) Long memberId,
            @RequestParam(required = false) String workCode,
            @RequestParam(required = false) String result,
            @RequestParam(required = false) String source,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize,
            HttpServletRequest request) {
        PageResult<UnlockRecordRow> page = unlocks.pageRecords(
                access, deptId, memberId, workCode, result, source, pageNo, pageSize);
        return ApiBody.ok(MemberViews.page(page.list().stream().map(this::toRecord).toList(), page.total()),
                MemberWebSupport.requestId(request));
    }

    @GetMapping("/unlock-record/export")
    public ResponseEntity<byte[]> export(
            @RequestParam(required = false) Long deptId,
            @RequestParam(required = false) Long memberId,
            @RequestParam(required = false) String result,
            @RequestParam(required = false) String source,
            HttpServletRequest request) {
        requireOps();
        byte[] body = UnlockXlsxExporter.export(unlocks.exportRecords(access, deptId, memberId, result, source));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=unlock-records.xlsx")
                .header(MemberWebSupport.REQUEST_ID, MemberWebSupport.requestId(request))
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(body);
    }

    private Map<String, Object> toRecord(UnlockRecordRow row) {
        Map<String, Object> data = UnlockViews.record(row);
        data.put("workCode", works.findById(row.workId()).map(WorkSnapshot::workCode).orElse(""));
        return data;
    }

    private void requireOps() {
        if (!access.ops()) {
            throw MemberFailures.forbidden();
        }
    }

    public record AdjustBody(String action, String workCode, String reason) {
    }
}
