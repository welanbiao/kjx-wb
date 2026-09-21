package com.koujunxin.work.packagecontent;

import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;

public record ManifestDocument(
        String id,
        String name,
        String type,
        String version,
        String form,
        String runtimeCompat,
        String entry,
        String contentVersion,
        String layout,
        String tokens,
        String music,
        List<String> permissions,
        List<String> allowedChannels,
        String promptMode,
        List<String> apps,
        List<String> tools,
        JsonNode audit,
        JsonNode budget
) {
    public static final Set<String> PERMISSIONS = Set.of(
            "ai.complete", "ai.tools", "state.save", "state.load", "asset.signed",
            "events.emit", "events.schedule", "metrics.report", "user.profile");

    public static final Set<String> CHANNELS = Set.of("PLATFORM", "BYOK");

    public static final Set<String> PROMPT_MODES = Set.of("SERVER", "PACKAGE", "BOTH");

    public static final Set<String> FORMS = Set.of("A", "B", "C");

    public static final Set<String> TYPES = Set.of("layout", "game");
}
