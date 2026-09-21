package com.koujunxin.tool.registry;

import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;
import com.koujunxin.tool.api.RateLimitSpec;
import com.koujunxin.tool.api.ToolFailure;

public record ToolRegistration(
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
        int retryMax
) {

    private static final Pattern CODE = Pattern.compile("^[a-z][a-z0-9_.]{2,48}$");

    public ToolRegistration {
        code = requireText(code, "code");
        if (!CODE.matcher(code).matches()) {
            throw new IllegalArgumentException("invalid tool code");
        }
        version = requireText(version, "version");
        type = requireText(type, "type").toLowerCase();
        if (!"builtin".equals(type) && !"custom".equals(type)) {
            throw new IllegalArgumentException("type must be builtin or custom");
        }
        displayName = requireText(displayName, "displayName");
        if (description == null) {
            description = "";
        }
        if (parametersSchema == null || !parametersSchema.isObject()) {
            throw new IllegalArgumentException("parameters schema is required");
        }
        if (implementation == null || !implementation.isObject()) {
            throw new IllegalArgumentException("implementation is required");
        }
        String kind = implementation.path("kind").asText("");
        if ("script".equalsIgnoreCase(kind)) {
            throw ToolFailure.of("TOOL_SCRIPT_FORBIDDEN", "script is not supported");
        }
        if (!"builtin".equalsIgnoreCase(kind) && !"compose".equalsIgnoreCase(kind)) {
            throw ToolFailure.of("TOOL_SCRIPT_FORBIDDEN", "implementation.kind must be builtin or compose");
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

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.trim();
    }
}
