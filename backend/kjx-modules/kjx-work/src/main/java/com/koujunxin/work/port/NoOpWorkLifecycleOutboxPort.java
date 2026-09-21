package com.koujunxin.work.port;

import com.koujunxin.work.catalog.WorkRecord;

public final class NoOpWorkLifecycleOutboxPort implements WorkLifecycleOutboxPort {

    @Override
    public void onPublished(WorkRecord work, String reason, String actor) {
    }

    @Override
    public void onUnpublished(WorkRecord work, String reason, String actor) {
    }
}
