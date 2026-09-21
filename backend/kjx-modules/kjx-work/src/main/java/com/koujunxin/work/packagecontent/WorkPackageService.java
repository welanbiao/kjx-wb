package com.koujunxin.work.packagecontent;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.koujunxin.work.asset.AssetService;
import com.koujunxin.work.asset.ObjectStoragePort;
import com.koujunxin.work.asset.StorageScope;
import com.koujunxin.work.catalog.CreateWorkVersionCommand;
import com.koujunxin.work.catalog.DuplicateVersionException;
import com.koujunxin.work.catalog.WorkCatalogService;
import com.koujunxin.work.catalog.WorkRecord;
import com.koujunxin.work.catalog.WorkVersionRepository;
import com.koujunxin.work.content.ContentLibraryService;
import com.koujunxin.work.music.MusicService;

public final class WorkPackageService {

    private static final Logger log = LoggerFactory.getLogger(WorkPackageService.class);

    private final WorkCatalogService catalog;
    private final WorkVersionRepository versions;
    private final WorkPackageRepository packages;
    private final ObjectStoragePort storage;
    private final WorkPackageInspector inspector;
    private final IsolatedZipExtractor extractor;
    private final AssetService assets;
    private final ContentLibraryService content;
    private final MusicService music;
    private final TransactionTemplate tx;
    private final WorkPackageLimits limits;
    private final ObjectMapper mapper;

    public WorkPackageService(
            WorkCatalogService catalog,
            WorkVersionRepository versions,
            WorkPackageRepository packages,
            ObjectStoragePort storage,
            WorkPackageInspector inspector,
            IsolatedZipExtractor extractor,
            AssetService assets,
            ContentLibraryService content,
            MusicService music,
            TransactionTemplate tx,
            WorkPackageLimits limits,
            ObjectMapper mapper) {
        this.catalog = catalog;
        this.versions = versions;
        this.packages = packages;
        this.storage = storage;
        this.inspector = inspector;
        this.extractor = extractor;
        this.assets = assets;
        this.content = content;
        this.music = music;
        this.tx = tx;
        this.limits = limits;
        this.mapper = mapper;
    }

    public WorkPackageRecord upload(long workId, String version, InputStream zipStream, String actor) {
        WorkRecord work = catalog.getById(workId);
        ensureVersion(workId, version, actor);
        Path zipFile = null;
        IsolatedZipExtractor.ExtractedPackage extracted = null;
        try {
            zipFile = Files.createTempFile("kjx-pkg-upload-", ".zip");
            StreamingUploads.HashedCopy hashed = StreamingUploads.copyLimited(zipStream, zipFile, limits.maxZipBytes());
            extracted = extractor.extract(zipFile);
            WorkPackageInspector.Inspection inspection = inspector.inspect(
                    extracted.root(), extracted.files(), work.workCode(), version);
            String reportJson = writeReport(inspection);
            String storageKey = "works/" + workId + "/" + version + "/source.zip";
            storage.put(StorageScope.SERVER, storageKey, Files.readAllBytes(zipFile), "application/zip", workId, version);
            WorkPackageStatus status = inspection.report().passed()
                    ? WorkPackageStatus.VALIDATED : WorkPackageStatus.REJECTED;
            if (inspection.report().passed()) {
                storeArtifacts(workId, version, extracted);
                assets.ingestPackageFiles(workId, version, extracted.root(), extracted.files(), actor);
                content.ingest(workId, version, extracted.root(), inspection.manifest(), actor);
                music.ingestBindings(workId, version, extracted.root(), inspection.manifest(), actor);
            }
            String cdnPrefix = "works/" + workId + "/" + version + "/cdn/";
            String serverPrefix = "works/" + workId + "/" + version + "/server/";
            String form = inspection.manifest() == null ? "A" : inspection.manifest().form();
            String contentVersion = inspection.manifest() == null ? null : inspection.manifest().contentVersion();
            WorkPackageRecord saved = packages.upsert(new WorkPackageRecord(
                    null, workId, version, status, form, hashed.sha256(), hashed.size(), storageKey,
                    cdnPrefix, serverPrefix, contentVersion, 0, null, reportJson, null, actor, Instant.now()));
            packages.touchVersionPackage(workId, version, storageKey, hashed.size(), hashed.sha256(), reportJson, 0);
            log.info("package uploaded workId={} version={} status={} files={}", workId, version, status,
                    extracted.files().size());
            if (!inspection.report().passed()) {
                throw WorkPackageException.rejected("PACKAGE_REJECTED", "package failed machine review",
                        inspection.report());
            }
            return saved;
        } catch (WorkPackageException ex) {
            throw ex;
        } catch (IOException ex) {
            throw WorkPackageException.badRequest("PACKAGE_UPLOAD_FAILED", "failed to read zip");
        } finally {
            if (extracted != null) {
                IsolatedZipExtractor.deleteRecursively(extracted.tempRoot());
            }
            if (zipFile != null) {
                try {
                    Files.deleteIfExists(zipFile);
                } catch (IOException ignored) {
                    // temp cleanup
                }
            }
        }
    }

    public ValidationReport validate(long workId, String version) {
        WorkPackageRecord pkg = requirePackage(workId, version);
        byte[] zip = storage.get(StorageScope.SERVER, pkg.storageKey())
                .orElseThrow(() -> WorkPackageException.notFound("stored zip missing"));
        Path zipFile = null;
        IsolatedZipExtractor.ExtractedPackage extracted = null;
        try {
            zipFile = Files.createTempFile("kjx-pkg-validate-", ".zip");
            Files.write(zipFile, zip);
            extracted = extractor.extract(zipFile);
            WorkRecord work = catalog.getById(workId);
            WorkPackageInspector.Inspection inspection = inspector.inspect(
                    extracted.root(), extracted.files(), work.workCode(), version);
            String reportJson = writeReport(inspection);
            WorkPackageStatus status = inspection.report().passed()
                    ? WorkPackageStatus.VALIDATED : WorkPackageStatus.REJECTED;
            packages.update(pkg.withReport(status, reportJson));
            packages.touchVersionPackage(workId, version, pkg.storageKey(), pkg.packageSize(), pkg.packageHash(),
                    reportJson, pkg.grayRatio());
            if (!inspection.report().passed()) {
                throw WorkPackageException.rejected("PACKAGE_REJECTED", "package failed machine review",
                        inspection.report());
            }
            return inspection.report();
        } catch (WorkPackageException ex) {
            throw ex;
        } catch (IOException ex) {
            throw WorkPackageException.badRequest("PACKAGE_VALIDATE_FAILED", "failed to revalidate zip");
        } finally {
            if (extracted != null) {
                IsolatedZipExtractor.deleteRecursively(extracted.tempRoot());
            }
            if (zipFile != null) {
                try {
                    Files.deleteIfExists(zipFile);
                } catch (IOException ignored) {
                    // temp cleanup
                }
            }
        }
    }

    public PackagePreview preview(long workId, String version) {
        WorkPackageRecord pkg = requireValidated(workId, version);
        byte[] zip = storage.get(StorageScope.SERVER, pkg.storageKey())
                .orElseThrow(() -> WorkPackageException.notFound("stored zip missing"));
        Path zipFile = null;
        IsolatedZipExtractor.ExtractedPackage extracted = null;
        try {
            zipFile = Files.createTempFile("kjx-pkg-preview-", ".zip");
            Files.write(zipFile, zip);
            extracted = extractor.extract(zipFile);
            Map<String, Object> layout = readObject(extracted.root(), "theme/layout.json");
            Map<String, Object> tokens = readObject(extracted.root(), "theme/tokens.json");
            Map<String, Object> contentMap = new LinkedHashMap<>();
            Path nodes = IsolatedZipExtractor.safeResolve(extracted.root(), "content/nodes.json");
            if (Files.isRegularFile(nodes)) {
                contentMap.put("nodes", mapper.readValue(Files.readString(nodes), Object.class));
            }
            Map<String, String> assetUrls = assets.previewUrls(workId, version);
            String token = UUID.randomUUID().toString().replace("-", "");
            packages.update(pkg.withPreview(token));
            return new PackagePreview(workId, version, pkg.form(), token,
                    Instant.now().plus(Duration.ofHours(1)), layout, tokens, contentMap, assetUrls);
        } catch (IOException ex) {
            throw WorkPackageException.badRequest("PACKAGE_PREVIEW_FAILED", "failed to build preview");
        } finally {
            if (extracted != null) {
                IsolatedZipExtractor.deleteRecursively(extracted.tempRoot());
            }
            if (zipFile != null) {
                try {
                    Files.deleteIfExists(zipFile);
                } catch (IOException ignored) {
                    // temp cleanup
                }
            }
        }
    }

    public WorkRecord publish(long workId, String version, int grayRatio, String reason, String actor) {
        if (grayRatio < 0 || grayRatio > 100) {
            throw WorkPackageException.badRequest("INVALID_GRAY_RATIO", "grayRatio must be 0-100");
        }
        if (reason == null || reason.isBlank()) {
            throw WorkPackageException.badRequest("AUDIT_REASON_REQUIRED", "reason is required");
        }
        WorkPackageRecord pkg = requireValidated(workId, version);
        return tx.execute(status -> {
            WorkRecord current = catalog.getById(workId);
            String previous = current.currentVersion();
            WorkRecord activated = catalog.activateWorkVersion(workId, version, reason, actor);
            if (pkg.contentVersion() != null && !pkg.contentVersion().isBlank()) {
                activated = catalog.activateContentVersion(workId, pkg.contentVersion(), reason, actor);
            }
            markVersionPublished(workId, version);
            WorkPackageRecord released = pkg.withRelease(previous, grayRatio, WorkPackageStatus.PUBLISHED);
            packages.update(released);
            packages.touchVersionPackage(workId, version, pkg.storageKey(), pkg.packageSize(), pkg.packageHash(),
                    pkg.validateReportJson(), grayRatio);
            log.info("package published workId={} version={} previous={} grayRatio={}", workId, version, previous,
                    grayRatio);
            return activated;
        });
    }

    public WorkRecord rollback(long workId, String version, String reason, String actor) {
        if (reason == null || reason.isBlank()) {
            throw WorkPackageException.badRequest("AUDIT_REASON_REQUIRED", "reason is required");
        }
        WorkPackageRecord pkg = requirePackage(workId, version);
        if (pkg.previousVersion() == null || pkg.previousVersion().isBlank()) {
            throw WorkPackageException.badRequest("PACKAGE_NO_PREVIOUS", "no previous version to restore");
        }
        String previous = pkg.previousVersion();
        WorkPackageRecord previousPkg = packages.find(workId, previous)
                .orElseThrow(() -> WorkPackageException.notFound("previous package not found: " + previous));
        return tx.execute(status -> {
            WorkRecord restored = catalog.activateWorkVersion(workId, previous, reason, actor);
            if (previousPkg.contentVersion() != null && !previousPkg.contentVersion().isBlank()) {
                restored = catalog.activateContentVersion(workId, previousPkg.contentVersion(), reason, actor);
            }
            forceVersionStatus(workId, version, "ROLLED_BACK");
            forceVersionStatus(workId, previous, "PUBLISHED");
            packages.update(pkg.withStatus(WorkPackageStatus.ROLLED_BACK, actor));
            packages.update(previousPkg.withStatus(WorkPackageStatus.PUBLISHED, actor));
            log.info("package rolled back workId={} from={} to={}", workId, version, previous);
            return restored;
        });
    }

    public WorkPackageRecord get(long workId, String version) {
        return requirePackage(workId, version);
    }

    public SignedUrlGuard signCdn(String objectKey) {
        CdnIsolation.rejectCdnKey(objectKey);
        return new SignedUrlGuard(storage.sign(StorageScope.CDN, objectKey, AssetService.DEFAULT_TTL).url());
    }

    private void storeArtifacts(long workId, String version, IsolatedZipExtractor.ExtractedPackage extracted)
            throws IOException {
        for (String relative : extracted.files()) {
            Path file = IsolatedZipExtractor.safeResolve(extracted.root(), relative);
            byte[] body = Files.readAllBytes(file);
            if (CdnIsolation.isServerPath(relative)) {
                String key = "works/" + workId + "/" + version + "/server/" + relative.substring("server/".length());
                storage.put(StorageScope.SERVER, key, body, "application/octet-stream", workId, version);
            } else if (!relative.startsWith("assets/")) {
                String key = "works/" + workId + "/" + version + "/cdn/" + relative;
                CdnIsolation.rejectCdnKey(key);
                storage.put(StorageScope.CDN, key, body, "application/octet-stream", workId, version);
            }
        }
    }

    private void ensureVersion(long workId, String version, String actor) {
        try {
            catalog.createWorkVersion(new CreateWorkVersionCommand(workId, version, "A", ">=1.0.0 <2.0.0", null, actor));
        } catch (DuplicateVersionException ignored) {
            versions.find(workId, version).orElseThrow();
        }
    }

    private void markVersionPublished(long workId, String version) {
        forceVersionStatus(workId, version, "PUBLISHED");
    }

    private void forceVersionStatus(long workId, String version, String toStatus) {
        String current = versions.find(workId, version).map(row -> row.status()).orElse(null);
        if (current == null || current.equals(toStatus)) {
            return;
        }
        versions.updateStatus(workId, version, current, toStatus);
    }

    private WorkPackageRecord requirePackage(long workId, String version) {
        return packages.find(workId, version)
                .orElseThrow(() -> WorkPackageException.notFound("package not found: " + version));
    }

    private WorkPackageRecord requireValidated(long workId, String version) {
        WorkPackageRecord pkg = requirePackage(workId, version);
        if (pkg.status() == WorkPackageStatus.REJECTED || pkg.status() == WorkPackageStatus.UPLOADED) {
            throw WorkPackageException.badRequest("PACKAGE_NOT_VALIDATED", "package must pass validation first");
        }
        return pkg;
    }

    private String writeReport(WorkPackageInspector.Inspection inspection) {
        try {
            return mapper.writeValueAsString(inspection.asMap());
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("failed to serialize report", ex);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readObject(Path root, String relative) throws IOException {
        Path file = IsolatedZipExtractor.safeResolve(root, relative);
        if (!Files.isRegularFile(file)) {
            return Map.of();
        }
        return mapper.readValue(Files.readString(file, StandardCharsets.UTF_8), Map.class);
    }

    /**
     * 仅测试/接口返回用；不要把 url 打进日志。
     */
    public record SignedUrlGuard(String url) {
        @Override
        public String toString() {
            return "SignedUrlGuard{url=REDACTED}";
        }
    }
}
