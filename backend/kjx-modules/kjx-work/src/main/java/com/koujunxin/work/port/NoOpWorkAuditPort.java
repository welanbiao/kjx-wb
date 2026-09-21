package com.koujunxin.work.port;

public final class NoOpWorkAuditPort implements WorkAuditPort {

    @Override
    public void record(long workId, String action, String reason, String actor) {
        // AUDIT 投影由 WP-50 / 集成装配接入。
    }
}
