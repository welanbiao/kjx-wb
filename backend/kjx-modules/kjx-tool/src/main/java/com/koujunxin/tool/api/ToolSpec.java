package com.koujunxin.tool.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.MissingNode;

public record ToolSpec(
        long id,
        String code,
        String version,
        String type,
        String displayName,
        String description,
        JsonNode parametersSchema,
        JsonNode preconditions,
        JsonNode implementation,
        RateLimitSpec rateLimit,
        int timeoutMs,
        int retryMax,
        boolean enabled
) {

    public ToolSpec {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("code is required");
        }
        if (version == null || version.isBlank()) {
            throw new IllegalArgumentException("version is required");
        }
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("type is required");
        }
        if (parametersSchema == null) {
            parametersSchema = MissingNode.getInstance();
        }
        if (preconditions == null) {
            preconditions = MissingNode.getInstance();
        }
        if (implementation == null) {
            implementation = MissingNode.getInstance();
        }
        if (rateLimit == null) {
            rateLimit = RateLimitSpec.unlimited();
        }
        if (timeoutMs <= 0) {
            timeoutMs = 5000;
        }
        if (retryMax < 0) {
            retryMax = 0;
        }
    }

    public String kind() {
        JsonNode kind = implementation.path("kind");
        return kind.isMissingNode() || kind.isNull() ? "" : kind.asText().trim();
    }

    public boolean builtinType() {
        return "builtin".equalsIgnoreCase(type);
    }

    public boolean composeKind() {
        return "compose".equalsIgnoreCase(kind());
    }

    public boolean scriptKind() {
        return "script".equalsIgnoreCase(kind());
    }
}
