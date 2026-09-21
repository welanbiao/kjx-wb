package com.koujunxin.work.content;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.koujunxin.work.packagecontent.IsolatedZipExtractor;

public final class ContentSchemaValidator {

    private static final Map<String, String> DOCS = Map.of(
            "content/nodes.json", "id",
            "content/characters.json", "id",
            "content/fragments.json", "id",
            "content/metrics.json", "key",
            "content/endings.json", "id"
    );

    private ContentSchemaValidator() {
    }

    public static List<String> validate(Path root, ObjectMapper mapper) throws IOException {
        List<String> errors = new ArrayList<>();
        Path nodes = IsolatedZipExtractor.safeResolve(root, "content/nodes.json");
        if (!Files.isRegularFile(nodes)) {
            errors.add("content/nodes.json missing");
            return errors;
        }
        for (Map.Entry<String, String> entry : DOCS.entrySet()) {
            Path file = IsolatedZipExtractor.safeResolve(root, entry.getKey());
            if (!Files.isRegularFile(file)) {
                if ("content/nodes.json".equals(entry.getKey())) {
                    errors.add(entry.getKey() + " missing");
                }
                continue;
            }
            JsonNode node = mapper.readTree(Files.readString(file, StandardCharsets.UTF_8));
            if (!node.isArray()) {
                errors.add(entry.getKey() + " must be array");
                continue;
            }
            for (int i = 0; i < node.size(); i++) {
                JsonNode item = node.get(i);
                if (!item.isObject() || !item.hasNonNull(entry.getValue())) {
                    errors.add(entry.getKey() + "[" + i + "] missing " + entry.getValue());
                    break;
                }
            }
        }
        Path layout = IsolatedZipExtractor.safeResolve(root, "theme/layout.json");
        if (Files.isRegularFile(layout)) {
            JsonNode node = mapper.readTree(Files.readString(layout, StandardCharsets.UTF_8));
            if (!node.isObject()) {
                errors.add("theme/layout.json must be object");
            }
        }
        Path tokens = IsolatedZipExtractor.safeResolve(root, "theme/tokens.json");
        if (Files.isRegularFile(tokens)) {
            JsonNode node = mapper.readTree(Files.readString(tokens, StandardCharsets.UTF_8));
            if (!node.isObject()) {
                errors.add("theme/tokens.json must be object");
            }
        }
        return errors;
    }
}
