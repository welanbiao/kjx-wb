package com.koujunxin.tool.api;

import com.fasterxml.jackson.databind.JsonNode;

public record RateLimitSpec(Integer perSession, Integer perDay, Integer cooldownSec) {

    public static RateLimitSpec unlimited() {
        return new RateLimitSpec(null, null, null);
    }

    public static RateLimitSpec fromJson(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode() || !node.isObject()) {
            return unlimited();
        }
        return new RateLimitSpec(intOrNull(node, "perSession"), intOrNull(node, "perDay"), intOrNull(node, "cooldownSec"));
    }

    public boolean limited() {
        return perSession != null || perDay != null || (cooldownSec != null && cooldownSec > 0);
    }

    private static Integer intOrNull(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || !value.isNumber()) {
            return null;
        }
        return value.asInt();
    }
}
