package com.koujunxin.notification.outbox;

/**
 * Outbox 轮询工作者。claim 与 publish 分离事务，保证至少一次投递。
 */
public interface OutboxWorker {

    int drain();
}
