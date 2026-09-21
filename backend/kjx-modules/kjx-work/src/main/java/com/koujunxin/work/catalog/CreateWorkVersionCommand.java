package com.koujunxin.work.catalog;

public record CreateWorkVersionCommand(
        long workId,
        String version,
        String form,
        String runtimeCompat,
        String entry,
        String actor
) {
}
