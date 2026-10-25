package com.koujunxin.server.integration;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.koujunxin.runtime.api.RuntimeFailure;
import com.koujunxin.runtime.api.SignedAsset;
import com.koujunxin.runtime.api.StageContent;
import com.koujunxin.runtime.api.WorkRuntimeManifest;
import com.koujunxin.runtime.api.WorkRuntimePort;
import com.koujunxin.work.asset.AssetRecord;
import com.koujunxin.work.asset.AssetService;
import com.koujunxin.work.asset.ObjectStoragePort;
import com.koujunxin.work.asset.SignedUrl;
import com.koujunxin.work.asset.StorageScope;
import com.koujunxin.work.packagecontent.WorkPackageException;

/**
 * 已发布作品运行时目录。只读 CDN 产物，拒绝 server/ 与 Prompt 母版。
 */
final class JdbcWorkRuntimeAdapter implements WorkRuntimePort {

    static final String REQUIRE_SQL = """
            SELECT w.id, w.work_code, w.current_version, w.content_version, w.access_form,
                   p.form AS package_form, p.content_version AS package_content_version
              FROM kjx_work w
              INNER JOIN kjx_work_package p
                ON p.work_id = w.id
               AND p.version = w.current_version
               AND p.status = 'PUBLISHED'
             WHERE w.work_code = ?
               AND w.deleted = b'0'
               AND w.current_version IS NOT NULL
               AND w.current_version <> ''
             LIMIT 1
            """;

    private final JdbcTemplate jdbc;
    private final ObjectProvider<AssetService> assets;
    private final ObjectProvider<ObjectStoragePort> storage;
    private final ObjectMapper mapper;

    JdbcWorkRuntimeAdapter(
            JdbcTemplate jdbc,
            ObjectProvider<AssetService> assets,
            ObjectProvider<ObjectStoragePort> storage,
            ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.assets = assets;
        this.storage = storage;
        this.mapper = mapper == null ? new ObjectMapper() : mapper;
    }

    @Override
    public WorkRuntimeManifest require(String workCode) {
        if (workCode == null || workCode.isBlank()) {
            throw RuntimeFailure.notFound("WORK_NOT_FOUND", "work not found");
        }
        List<PublishedWork> rows = jdbc.query(
                REQUIRE_SQL,
                (rs, rowNum) -> new PublishedWork(
                        rs.getLong("id"),
                        rs.getString("work_code"),
                        rs.getString("current_version"),
                        rs.getString("content_version"),
                        rs.getString("access_form"),
                        rs.getString("package_form"),
                        rs.getString("package_content_version")),
                workCode.trim());
        if (rows.isEmpty()) {
            throw RuntimeFailure.notFound("WORK_NOT_FOUND", "published work not found");
        }
        PublishedWork work = rows.get(0);
        String form = firstNonBlank(work.packageForm(), work.accessForm(), "A");
        String packageVersion = work.currentVersion();
        String contentVersion = firstNonBlank(work.packageContentVersion(), work.contentVersion(), packageVersion);
        String entry = ("B".equalsIgnoreCase(form) || "C".equalsIgnoreCase(form)) ? "dist/index.html" : "";
        return new WorkRuntimeManifest(
                work.id(),
                work.workCode(),
                form,
                packageVersion,
                contentVersion,
                "1",
                entry,
                sandboxOrigin(work.workCode()));
    }

    @Override
    public StageContent loadStage(String workCode, String packageVersion, String stage, List<String> reached) {
        WorkRuntimeManifest manifest = require(workCode);
        String version = firstNonBlank(packageVersion, manifest.packageVersion());
        String layout = readCdnJson(manifest.workId(), version, "theme/layout.json", "{}");
        String tokens = readCdnJson(manifest.workId(), version, "theme/tokens.json", "{}");
        String nodesRaw = readCdnJson(manifest.workId(), version, "content/nodes.json", "[]");
        String nodes = filterNodes(nodesRaw, stage, reached);
        List<String> assetKeys = new ArrayList<>();
        Map<String, String> signed = new LinkedHashMap<>();
        AssetService assetService = assets == null ? null : assets.getIfAvailable();
        if (assetService != null) {
            for (AssetRecord asset : assetService.list(manifest.workId(), version)) {
                if (asset == null || asset.scope() != StorageScope.CDN) {
                    continue;
                }
                if (containsServer(asset.assetKey())
                        || containsServer(asset.path())
                        || containsServer(asset.storageKey())) {
                    continue;
                }
                assetKeys.add(asset.assetKey());
                try {
                    SignedUrl url = assetService.sign(manifest.workId(), version, asset.assetKey());
                    if (url != null && url.url() != null && !containsServer(url.url()) && !containsServer(url.objectKey())) {
                        signed.put(asset.assetKey(), url.url());
                    }
                } catch (RuntimeException ignored) {
                    // 单个签名失败跳过，不影响阶段内容。
                }
            }
        }
        return new StageContent(stage, layout, tokens, nodes, assetKeys, signed, false);
    }

    @Override
    public SignedAsset signAsset(String workCode, String packageVersion, String assetKey, String stage) {
        if (containsServer(assetKey)) {
            throw RuntimeFailure.forbidden("SERVER_NOT_ON_CDN", "server/ artifacts cannot be signed");
        }
        WorkRuntimeManifest manifest = require(workCode);
        String version = firstNonBlank(packageVersion, manifest.packageVersion());
        String objectKey = "works/" + manifest.workId() + "/" + version + "/cdn/" + (assetKey == null ? "" : assetKey);
        if (containsServer(version) || containsServer(objectKey)) {
            throw RuntimeFailure.forbidden("SERVER_NOT_ON_CDN", "server/ artifacts cannot be signed");
        }
        AssetService assetService = assets == null ? null : assets.getIfAvailable();
        if (assetService == null) {
            throw RuntimeFailure.notFound("ASSET_SIGNER_UNAVAILABLE", "AssetService is not available");
        }
        try {
            SignedUrl signed = assetService.sign(manifest.workId(), version, assetKey);
            if (signed == null || containsServer(signed.url()) || containsServer(signed.objectKey())) {
                throw RuntimeFailure.forbidden("SERVER_NOT_ON_CDN", "server/ artifacts cannot be signed");
            }
            return new SignedAsset(assetKey, signed.url(), signed.expiresAt());
        } catch (RuntimeFailure ex) {
            throw ex;
        } catch (WorkPackageException ex) {
            if ("SERVER_NOT_ON_CDN".equals(ex.errorName())) {
                throw RuntimeFailure.forbidden("SERVER_NOT_ON_CDN", "server/ artifacts cannot be signed");
            }
            if (ex.httpStatus() == 404) {
                throw RuntimeFailure.notFound("ASSET_NOT_FOUND", "asset not found");
            }
            throw RuntimeFailure.badRequest(ex.errorName(), "asset sign failed");
        }
    }

    @Override
    public String sandboxOrigin(String workCode) {
        if (workCode == null || !workCode.matches("[a-zA-Z0-9-]+")) {
            return "null";
        }
        return "https://" + workCode + ".game.koujunxin.com";
    }

    private String readCdnJson(long workId, String version, String relative, String missing) {
        ObjectStoragePort port = storage == null ? null : storage.getIfAvailable();
        if (port == null) {
            return missing;
        }
        String key = "works/" + workId + "/" + version + "/cdn/" + relative;
        if (containsServer(key)) {
            return missing;
        }
        try {
            return port.get(StorageScope.CDN, key)
                    .map(body -> new String(body, StandardCharsets.UTF_8))
                    .filter(text -> text != null && !text.isBlank())
                    .orElse(missing);
        } catch (RuntimeException ignored) {
            return missing;
        }
    }

    private String filterNodes(String nodesJson, String stage, List<String> reached) {
        if (nodesJson == null || nodesJson.isBlank()) {
            return "[]";
        }
        try {
            JsonNode root = mapper.readTree(nodesJson);
            if (!root.isArray()) {
                return nodesJson;
            }
            ArrayNode kept = mapper.createArrayNode();
            List<String> reachedStages = reached == null ? List.of() : reached;
            for (JsonNode node : root) {
                if (visible(node, stage, reachedStages)) {
                    kept.add(node);
                }
            }
            return mapper.writeValueAsString(kept);
        } catch (Exception ex) {
            return nodesJson;
        }
    }

    private static boolean visible(JsonNode node, String stage, List<String> reached) {
        if (!node.isObject() || !node.hasNonNull("stage")) {
            return true;
        }
        JsonNode value = node.get("stage");
        String required = value.isNumber() ? Integer.toString(value.asInt()) : value.asText();
        if (required == null || required.isBlank()) {
            return true;
        }
        if (required.equals(stage)) {
            return true;
        }
        return reached.contains(required);
    }

    static boolean containsServer(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String path = value.replace('\\', '/').toLowerCase(Locale.ROOT);
        return path.equals("server")
                || path.startsWith("server/")
                || path.contains("/server/")
                || path.contains("/scope-server/");
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    record PublishedWork(
            long id,
            String workCode,
            String currentVersion,
            String contentVersion,
            String accessForm,
            String packageForm,
            String packageContentVersion) {
    }
}
