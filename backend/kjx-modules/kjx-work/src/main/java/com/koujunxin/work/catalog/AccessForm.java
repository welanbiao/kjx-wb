package com.koujunxin.work.catalog;

public enum AccessForm {
    A,
    B,
    C;

    public static AccessForm from(String raw) {
        if (raw == null || raw.isBlank()) {
            return A;
        }
        return AccessForm.valueOf(raw.trim());
    }
}
