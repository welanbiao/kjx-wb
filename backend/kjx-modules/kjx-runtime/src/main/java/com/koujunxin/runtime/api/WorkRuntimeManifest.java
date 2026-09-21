package com.koujunxin.runtime.api;

/**
 * 已发布作品的运行时描述。不含 server/ 目录与 Prompt。
 */
public record WorkRuntimeManifest(
        long workId,
        String workCode,
        String form,
        String packageVersion,
        String contentVersion,
        String schemaVersion,
        String entry,
        String sandboxOrigin
) {

    public WorkRuntimeManifest {
        if (workId <= 0) {
            throw new IllegalArgumentException("workId must be positive");
        }
        if (workCode == null || workCode.isBlank()) {
            throw new IllegalArgumentException("workCode is required");
        }
        form = form == null || form.isBlank() ? "A" : form.trim();
        packageVersion = require(packageVersion, "packageVersion");
        contentVersion = require(contentVersion, "contentVersion");
        schemaVersion = require(schemaVersion, "schemaVersion");
        if (entry == null) {
            entry = "";
        }
        if (sandboxOrigin == null || sandboxOrigin.isBlank()) {
            sandboxOrigin = "null";
        }
    }

    private static String require(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.trim();
    }
}
