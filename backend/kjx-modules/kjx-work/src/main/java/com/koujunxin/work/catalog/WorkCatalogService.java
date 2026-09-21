package com.koujunxin.work.catalog;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import org.springframework.transaction.support.TransactionTemplate;

import com.koujunxin.work.api.WorkCatalogQueryPort;
import com.koujunxin.work.api.WorkSnapshot;
import com.koujunxin.work.port.WorkAuditPort;
import com.koujunxin.work.port.WorkLifecycleOutboxPort;

public class WorkCatalogService implements WorkCatalogQueryPort {

    public static final Pattern WORK_CODE = Pattern.compile("^[a-z0-9][a-z0-9-]{1,62}$");
    public static final Pattern VERSION = Pattern.compile("^[A-Za-z0-9._-]{1,32}$");

    private final WorkRepository works;
    private final WorkVersionRepository workVersions;
    private final ContentVersionRepository contentVersions;
    private final TransactionTemplate tx;
    private final WorkAuditPort auditPort;
    private final WorkLifecycleOutboxPort outboxPort;

    public WorkCatalogService(
            WorkRepository works,
            WorkVersionRepository workVersions,
            ContentVersionRepository contentVersions,
            TransactionTemplate tx,
            WorkAuditPort auditPort,
            WorkLifecycleOutboxPort outboxPort) {
        this.works = works;
        this.workVersions = workVersions;
        this.contentVersions = contentVersions;
        this.tx = tx;
        this.auditPort = auditPort;
        this.outboxPort = outboxPort;
    }

    public WorkRecord create(CreateWorkCommand command) {
        requireReason(command.reason());
        validateCode(command.workCode());
        if (command.name() == null || command.name().isBlank()) {
            throw new WorkDomainException(400, "INVALID_WORK", "name is required");
        }
        if (command.workType() == null) {
            throw new WorkDomainException(400, "INVALID_WORK", "workType is required");
        }
        int grayRatio = command.grayRatio() == null ? 0 : command.grayRatio();
        validateGrayRatio(grayRatio);
        WorkRecord draft = new WorkRecord(
                null,
                command.workCode().trim(),
                command.name().trim(),
                command.workType(),
                blankToNull(command.summary()),
                blankToNull(command.coverUrl()),
                command.screenshots(),
                blankToNull(command.category()),
                command.tags(),
                WorkStatus.DRAFT,
                null,
                null,
                command.accessForm() == null ? AccessForm.A : command.accessForm(),
                "SERVER",
                List.of("PLATFORM", "BYOK"),
                false,
                List.of(),
                List.of(),
                List.of(),
                null,
                grayRatio,
                command.sortWeight() == null ? 0 : command.sortWeight(),
                0,
                null,
                command.actor(),
                null,
                command.actor(),
                null,
                false);
        return tx.execute(status -> {
            WorkRecord created = works.insert(draft);
            auditPort.record(created.id(), "CREATE", command.reason(), command.actor());
            return created;
        });
    }

    public WorkRecord update(UpdateWorkCommand command) {
        requireReason(command.reason());
        if (command.grayRatio() != null) {
            validateGrayRatio(command.grayRatio());
        }
        return tx.execute(status -> {
            WorkRecord current = works.findById(command.workId())
                    .orElseThrow(() -> new WorkNotFoundException(command.workId()));
            if (command.expectedUpdateTime() != null
                    && current.updateTime() != null
                    && !current.updateTime().equals(command.expectedUpdateTime())) {
                throw new WorkOptimisticLockException(command.workId());
            }
            WorkStatus nextStatus = command.status() == null ? current.status() : command.status();
            WorkStateMachine.requireTransit(current.status(), nextStatus);
            Instant publishTime = current.publishTime();
            if (WorkStateMachine.shouldStampPublishTime(current.status(), nextStatus)) {
                publishTime = Instant.now();
            }
            WorkRecord next = new WorkRecord(
                    current.id(),
                    current.workCode(),
                    firstNonBlank(command.name(), current.name()),
                    current.workType(),
                    command.summary() != null ? blankToNull(command.summary()) : current.summary(),
                    command.coverUrl() != null ? blankToNull(command.coverUrl()) : current.coverUrl(),
                    command.screenshots() != null ? command.screenshots() : current.screenshots(),
                    command.category() != null ? blankToNull(command.category()) : current.category(),
                    command.tags() != null ? command.tags() : current.tags(),
                    nextStatus,
                    current.currentVersion(),
                    current.contentVersion(),
                    command.accessForm() != null ? command.accessForm() : current.accessForm(),
                    current.promptMode(),
                    current.allowedChannels(),
                    current.allowByokDirect(),
                    current.apps(),
                    current.tools(),
                    current.serverTools(),
                    current.pricingMode(),
                    command.grayRatio() != null ? command.grayRatio() : current.grayRatio(),
                    command.sortWeight() != null ? command.sortWeight() : current.sortWeight(),
                    current.unlockCount(),
                    publishTime,
                    current.creator(),
                    current.createTime(),
                    command.actor(),
                    current.updateTime(),
                    current.deleted());
            int rows = works.updateCas(current, next);
            if (rows == 0) {
                throw new WorkOptimisticLockException(command.workId());
            }
            WorkRecord saved = works.findById(command.workId()).orElseThrow();
            auditPort.record(saved.id(), "UPDATE", command.reason(), command.actor());
            if (WorkStateMachine.isPublishEvent(current.status(), nextStatus)) {
                outboxPort.onPublished(saved, command.reason(), command.actor());
            }
            if (WorkStateMachine.isUnpublishEvent(current.status(), nextStatus)) {
                outboxPort.onUnpublished(saved, command.reason(), command.actor());
            }
            return saved;
        });
    }

    public WorkRecord getById(long workId) {
        return works.findById(workId).orElseThrow(() -> new WorkNotFoundException(workId));
    }

    public WorkRecord getByCode(String workCode) {
        return works.findByCode(workCode).orElseThrow(() -> new WorkNotFoundException(workCode));
    }

    public PageData<WorkRecord> pageAdmin(AdminWorkQuery query) {
        return new PageData<>(works.listAdmin(query), works.countAdmin(query));
    }

    public WorkVersionRecord createWorkVersion(CreateWorkVersionCommand command) {
        validateVersion(command.version());
        return tx.execute(status -> {
            requireWork(command.workId());
            return workVersions.insert(new WorkVersionRecord(
                    null, command.workId(), command.version().trim(), "DRAFT",
                    command.form() == null ? "A" : command.form(),
                    command.runtimeCompat(), command.entry(), null, null, null, 0, null,
                    command.actor(), null));
        });
    }

    public ContentVersionRecord createContentVersion(CreateContentVersionCommand command) {
        validateVersion(command.version());
        return tx.execute(status -> {
            requireWork(command.workId());
            return contentVersions.insert(new ContentVersionRecord(
                    null, command.workId(), command.version().trim(), "DRAFT",
                    Math.max(command.nodeCount(), 0), command.contentUrl(), command.contentJson(),
                    null, null, command.actor(), null));
        });
    }

    /**
     * 只切换作品代码版本指针，不改 content_version。
     */
    public WorkRecord activateWorkVersion(long workId, String version, String reason, String actor) {
        requireReason(reason);
        validateVersion(version);
        return tx.execute(status -> {
            WorkRecord current = requireWork(workId);
            WorkVersionRecord versionRow = workVersions.find(workId, version)
                    .orElseThrow(() -> new WorkDomainException(404, "WORK_VERSION_NOT_FOUND",
                            "work version not found: " + version));
            WorkRecord next = current.withVersions(versionRow.version(), current.contentVersion());
            next = copyForUpdate(next, actor);
            if (works.updateCas(current, next) == 0) {
                throw new WorkOptimisticLockException(workId);
            }
            auditPort.record(workId, "ACTIVATE_WORK_VERSION", reason, actor);
            return works.findById(workId).orElseThrow();
        });
    }

    /**
     * 只切换内容版本指针，不改 current_version。
     */
    public WorkRecord activateContentVersion(long workId, String version, String reason, String actor) {
        requireReason(reason);
        validateVersion(version);
        return tx.execute(status -> {
            WorkRecord current = requireWork(workId);
            ContentVersionRecord versionRow = contentVersions.find(workId, version)
                    .orElseThrow(() -> new WorkDomainException(404, "CONTENT_VERSION_NOT_FOUND",
                            "content version not found: " + version));
            WorkRecord next = current.withVersions(current.currentVersion(), versionRow.version());
            next = copyForUpdate(next, actor);
            if (works.updateCas(current, next) == 0) {
                throw new WorkOptimisticLockException(workId);
            }
            auditPort.record(workId, "ACTIVATE_CONTENT_VERSION", reason, actor);
            return works.findById(workId).orElseThrow();
        });
    }

    public List<WorkVersionRecord> listWorkVersions(long workId) {
        requireWork(workId);
        return workVersions.listByWorkId(workId);
    }

    public List<ContentVersionRecord> listContentVersions(long workId) {
        requireWork(workId);
        return contentVersions.listByWorkId(workId);
    }

    @Override
    public Optional<WorkSnapshot> findByCode(String workCode) {
        return works.findByCode(workCode).map(WorkSnapshot::from);
    }

    @Override
    public Optional<WorkSnapshot> findById(long workId) {
        return works.findById(workId).map(WorkSnapshot::from);
    }

    private WorkRecord requireWork(long workId) {
        return works.findById(workId).orElseThrow(() -> new WorkNotFoundException(workId));
    }

    private static WorkRecord copyForUpdate(WorkRecord next, String actor) {
        return new WorkRecord(
                next.id(), next.workCode(), next.name(), next.workType(), next.summary(), next.coverUrl(),
                next.screenshots(), next.category(), next.tags(), next.status(), next.currentVersion(),
                next.contentVersion(), next.accessForm(), next.promptMode(), next.allowedChannels(),
                next.allowByokDirect(), next.apps(), next.tools(), next.serverTools(), next.pricingMode(),
                next.grayRatio(), next.sortWeight(), next.unlockCount(), next.publishTime(),
                next.creator(), next.createTime(), actor, next.updateTime(), next.deleted());
    }

    private static void validateCode(String workCode) {
        if (workCode == null || !WORK_CODE.matcher(workCode.trim()).matches()) {
            throw new WorkDomainException(400, "INVALID_WORK_CODE", "workCode must match ^[a-z0-9][a-z0-9-]{1,62}$");
        }
    }

    private static void validateVersion(String version) {
        if (version == null || !VERSION.matcher(version.trim()).matches()) {
            throw new WorkDomainException(400, "INVALID_VERSION", "version is invalid");
        }
    }

    private static void validateGrayRatio(int grayRatio) {
        if (grayRatio < 0 || grayRatio > 100) {
            throw new WorkDomainException(400, "INVALID_GRAY_RATIO", "grayRatio must be 0-100");
        }
    }

    private static void requireReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new WorkDomainException(400, "AUDIT_REASON_REQUIRED", "reason is required");
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String firstNonBlank(String candidate, String fallback) {
        return candidate == null || candidate.isBlank() ? fallback : candidate.trim();
    }

    public record PageData<T>(List<T> list, long total) {
    }
}
