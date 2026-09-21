package com.koujunxin.work.packagecontent.web;

import java.io.IOException;
import java.util.Map;

import org.dromara.common.satoken.utils.StpKit;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.koujunxin.work.catalog.WorkRecord;
import com.koujunxin.work.packagecontent.PackagePreview;
import com.koujunxin.work.packagecontent.ValidationReport;
import com.koujunxin.work.packagecontent.WorkPackageRecord;
import com.koujunxin.work.packagecontent.WorkPackageService;
import com.koujunxin.work.web.ApiEnvelope;

@RestController
@RequestMapping("/admin-api/kjx/work/{workId}/versions/{version}/package")
@ConditionalOnBean(WorkPackageService.class)
public class AdminWorkPackageController {

    private final WorkPackageService packages;
    private final ObjectMapper mapper;

    public AdminWorkPackageController(WorkPackageService packages, ObjectMapper mapper) {
        this.packages = packages;
        this.mapper = mapper;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiEnvelope<WorkPackageRecord> upload(
            @PathVariable long workId,
            @PathVariable String version,
            @RequestPart("file") MultipartFile file,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId) throws IOException {
        WorkPackageRecord saved = packages.upload(workId, version, file.getInputStream(), actor());
        return ApiEnvelope.ok(requestId, saved);
    }

    @PostMapping("/validate")
    public ApiEnvelope<ValidationReport> validate(
            @PathVariable long workId,
            @PathVariable String version,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId) {
        return ApiEnvelope.ok(requestId, packages.validate(workId, version));
    }

    @GetMapping
    public ApiEnvelope<WorkPackageRecord> get(
            @PathVariable long workId,
            @PathVariable String version,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId) {
        return ApiEnvelope.ok(requestId, packages.get(workId, version));
    }

    @GetMapping("/report")
    public ApiEnvelope<Map<String, Object>> report(
            @PathVariable long workId,
            @PathVariable String version,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId) throws Exception {
        WorkPackageRecord pkg = packages.get(workId, version);
        Map<String, Object> body = pkg.validateReportJson() == null
                ? Map.of()
                : mapper.readValue(pkg.validateReportJson(), new TypeReference<>() {
                });
        return ApiEnvelope.ok(requestId, body);
    }

    @PostMapping("/preview")
    public ApiEnvelope<PackagePreview> preview(
            @PathVariable long workId,
            @PathVariable String version,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId) {
        return ApiEnvelope.ok(requestId, packages.preview(workId, version));
    }

    @PostMapping("/publish")
    public ApiEnvelope<WorkRecord> publish(
            @PathVariable long workId,
            @PathVariable String version,
            @RequestBody PublishBody body,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId) {
        int gray = body == null || body.grayRatio() == null ? 0 : body.grayRatio();
        String reason = body == null ? null : body.reason();
        return ApiEnvelope.ok(requestId, packages.publish(workId, version, gray, reason, actor()));
    }

    @PostMapping("/rollback")
    public ApiEnvelope<WorkRecord> rollback(
            @PathVariable long workId,
            @PathVariable String version,
            @RequestBody PublishBody body,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId) {
        String reason = body == null ? null : body.reason();
        return ApiEnvelope.ok(requestId, packages.rollback(workId, version, reason, actor()));
    }

    private static String actor() {
        try {
            return String.valueOf(StpKit.ADMIN.getLoginId());
        } catch (Exception ex) {
            return "admin";
        }
    }

    public record PublishBody(Integer grayRatio, String reason) {
    }
}
