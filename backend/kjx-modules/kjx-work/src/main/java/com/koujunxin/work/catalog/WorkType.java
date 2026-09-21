package com.koujunxin.work.catalog;

public enum WorkType {
    layout,
    game;

    public static WorkType from(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("workType is required");
        }
        return WorkType.valueOf(raw.trim());
    }
}
