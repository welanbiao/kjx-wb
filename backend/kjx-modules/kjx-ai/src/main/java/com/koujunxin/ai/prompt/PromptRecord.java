package com.koujunxin.ai.prompt;

public record PromptRecord(
        String workCode,
        PromptKind kind,
        String promptMode,
        String version,
        PromptStatus status,
        String stablePrefix,
        String toolSpecJson,
        String variableSchemaJson
) {

    public PromptRecord {
        if (workCode == null || workCode.isBlank()) {
            throw new IllegalArgumentException("workCode is required");
        }
        if (kind == null) {
            throw new IllegalArgumentException("kind is required");
        }
        if (promptMode == null || promptMode.isBlank()) {
            promptMode = "SERVER";
        }
        if (version == null || version.isBlank()) {
            throw new IllegalArgumentException("version is required");
        }
        if (status == null) {
            status = PromptStatus.DRAFT;
        }
        if (stablePrefix == null) {
            stablePrefix = "";
        }
        if (toolSpecJson == null || toolSpecJson.isBlank()) {
            toolSpecJson = "[]";
        }
        if (variableSchemaJson == null || variableSchemaJson.isBlank()) {
            variableSchemaJson = "{}";
        }
    }

    public PromptRecord withStatus(PromptStatus next) {
        return new PromptRecord(workCode, kind, promptMode, version, next, stablePrefix, toolSpecJson, variableSchemaJson);
    }

    @Override
    public String toString() {
        return "PromptRecord[workCode=" + workCode
                + ", kind=" + kind
                + ", version=" + version
                + ", status=" + status
                + ", prefixChars=" + stablePrefix.length()
                + "]";
    }
}
