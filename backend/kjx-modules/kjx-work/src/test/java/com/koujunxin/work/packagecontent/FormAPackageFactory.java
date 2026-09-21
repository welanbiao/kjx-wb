package com.koujunxin.work.packagecontent;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import com.fasterxml.jackson.databind.ObjectMapper;

public final class FormAPackageFactory {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    static final String SAFE_SVG = """
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 8 8"><rect width="8" height="8" fill="#3366ff"/></svg>
            """;

    private FormAPackageFactory() {
    }

    public static Map<String, byte[]> sampleFiles(String workCode, String version) throws IOException {
        Map<String, byte[]> files = new LinkedHashMap<>();
        files.put("manifest.json", json("""
                {
                  "id": "%s",
                  "name": "Form A Demo",
                  "type": "layout",
                  "version": "%s",
                  "form": "A",
                  "runtimeCompat": ">=1.0.0 <2.0.0",
                  "contentVersion": "c-%s",
                  "layout": "theme/layout.json",
                  "tokens": "theme/tokens.json",
                  "music": "theme/music.json",
                  "permissions": ["ai.complete", "state.save", "state.load", "asset.signed"],
                  "allowedChannels": ["PLATFORM"],
                  "promptMode": "SERVER",
                  "apps": [],
                  "tools": [],
                  "audit": {"copyright": "in-house", "contentRating": "12+", "source": "original"}
                }
                """.formatted(workCode, version, version)));
        files.put("theme/layout.json", json("{\"type\":\"phone\",\"screens\":[{\"id\":\"home\"}]}"));
        files.put("theme/tokens.json", json("{\"color\":{\"bg\":\"#111111\"}}"));
        files.put("theme/music.json", json("{\"packs\":[\"demo\"],\"scenes\":{\"intro\":{\"fixed\":\"track_001\"}}}"));
        files.put("assets/assets.manifest.json", json("{\"files\":[{\"path\":\"assets/images/icon.svg\",\"sha256\":\"pending\"}]}"));
        files.put("assets/images/icon.svg", SAFE_SVG.getBytes(StandardCharsets.UTF_8));
        files.put("content/nodes.json", json("[{\"id\":\"n1\",\"text\":\"hello\",\"stage\":1}]"));
        files.put("content/characters.json", json("[{\"id\":\"c1\",\"name\":\"A\"}]"));
        files.put("content/fragments.json", json("[{\"id\":\"f1\",\"kind\":\"memo\"}]"));
        files.put("content/metrics.json", json("[{\"key\":\"affection\",\"min\":0,\"max\":100}]"));
        files.put("content/endings.json", json("[{\"id\":\"good\",\"title\":\"Good\"}]"));
        files.put("server/prompts/system.md", "# system prompt, never on CDN".getBytes(StandardCharsets.UTF_8));
        files.put("server/protocol.schema.json", json("{\"type\":\"object\",\"properties\":{\"ok\":{\"type\":\"boolean\"}}}"));
        files.put("server/tools.json", json("[]"));
        writeChecksums(files);
        files.put("assets/assets.manifest.json", json("{\"files\":[{\"path\":\"assets/images/icon.svg\",\"sha256\":\""
                + StreamingUploads.hex(files.get("assets/images/icon.svg")) + "\"}]}"));
        writeChecksums(files);
        return files;
    }

    public static byte[] sampleZip(String workCode, String version) throws IOException {
        return zip(sampleFiles(workCode, version));
    }

    public static byte[] mutate(String workCode, String version, Consumer<Map<String, byte[]>> mutator) throws IOException {
        Map<String, byte[]> files = sampleFiles(workCode, version);
        mutator.accept(files);
        writeChecksums(files);
        return zip(files);
    }

    public static byte[] zip(Map<String, byte[]> files) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(buffer)) {
            for (Map.Entry<String, byte[]> entry : files.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue());
                zip.closeEntry();
            }
        }
        return buffer.toByteArray();
    }

    private static void writeChecksums(Map<String, byte[]> files) throws IOException {
        Map<String, String> sums = new LinkedHashMap<>();
        for (Map.Entry<String, byte[]> entry : files.entrySet()) {
            if ("checksums.json".equals(entry.getKey())) {
                continue;
            }
            sums.put(entry.getKey(), StreamingUploads.hex(entry.getValue()));
        }
        files.put("checksums.json", MAPPER.writeValueAsBytes(sums));
    }

    private static byte[] json(String raw) {
        return raw.getBytes(StandardCharsets.UTF_8);
    }
}
