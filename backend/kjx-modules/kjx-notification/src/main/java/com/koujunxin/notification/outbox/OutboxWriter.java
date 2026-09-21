package com.koujunxin.notification.outbox;

/**
 * 业务事务内写入 Outbox。必须与业务同一 JDBC 事务。
 */
public interface OutboxWriter {

    String append(OutboxAppendCommand command);
}
