package com.koujunxin.work.content;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.koujunxin.work.catalog.ContentVersionRecord;
import com.koujunxin.work.catalog.CreateContentVersionCommand;
import com.koujunxin.work.catalog.DuplicateVersionException;
import com.koujunxin.work.catalog.WorkCatalogService;
import com.koujunxin.work.packagecontent.IsolatedZipExtractor;
import com.koujunxin.work.packagecontent.ManifestDocument;

public final class ContentLibraryService {

    private static final Map<String, String> FILES = Map.of(
            "nodes", "content/nodes.json",
            "characters", "content/characters.json",
            "fragments", "content/fragments.json",
            "metrics", "content/metrics.json",
            "endings", "content/endings.json"
    );

    private final ContentDocumentRepository documents;
    private final WorkCatalogService catalog;
    private final ObjectMapper mapper;

    public ContentLibraryService(
            ContentDocumentRepository documents, WorkCatalogService catalog, ObjectMapper mapper) {
        this.documents = documents;
        this.catalog = catalog;
        this.mapper = mapper;
    }

    public void ingest(long workId, String workVersion, Path root, ManifestDocument manifest, String actor)
            throws IOException {
        int nodeCount = 0;
        Map<String, JsonNode> bundle = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : FILES.entrySet()) {
            Path file = IsolatedZipExtractor.safeResolve(root, entry.getValue());
            if (!Files.isRegularFile(file)) {
                continue;
            }
            String json = Files.readString(file, StandardCharsets.UTF_8);
            JsonNode node = mapper.readTree(json);
            int count = node.isArray() ? node.size() : 1;
            if ("nodes".equals(entry.getKey())) {
                nodeCount = count;
            }
            documents.upsert(new ContentDocumentRecord(
                    null, workId, workVersion, entry.getKey(), json, count, actor, Instant.now()));
            bundle.put(entry.getKey(), node);
        }
        String contentVersion = manifest == null ? workVersion : manifest.contentVersion();
        if (contentVersion == null || contentVersion.isBlank()) {
            return;
        }
        String bundleJson = mapper.writeValueAsString(bundle);
        try {
            catalog.createContentVersion(new CreateContentVersionCommand(
                    workId, contentVersion, nodeCount, null, bundleJson, actor));
        } catch (DuplicateVersionException ignored) {
            // keep existing content version row; documents already upserted
        }
    }

    public List<ContentDocumentRecord> list(long workId, String version) {
        return documents.list(workId, version);
    }

    public ContentVersionRecord requireCatalogVersion(long workId, String contentVersion) {
        return catalog.listContentVersions(workId).stream()
                .filter(row -> row.version().equals(contentVersion))
                .findFirst()
                .orElse(null);
    }
}
