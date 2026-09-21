package com.koujunxin.notification.outbox;

/**
 * 投递 SPI。由通知/投影模块实现。失败由 Worker 重试，不得在业务事务内调用。
 */
public interface OutboxPublisher {

    void publish(OutboxRecord record) throws Exception;
}
