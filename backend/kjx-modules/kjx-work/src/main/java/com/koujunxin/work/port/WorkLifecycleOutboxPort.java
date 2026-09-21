package com.koujunxin.work.port;

import com.koujunxin.work.catalog.WorkRecord;

/**
 * 发布/下架副作用。实现应在当前事务内调用 {@code OutboxWriter}。
 */
public interface WorkLifecycleOutboxPort {

    void onPublished(WorkRecord work, String reason, String actor);

    void onUnpublished(WorkRecord work, String reason, String actor);
}
