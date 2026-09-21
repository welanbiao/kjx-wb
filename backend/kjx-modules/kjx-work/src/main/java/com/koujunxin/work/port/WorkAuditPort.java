package com.koujunxin.work.port;

/**
 * 关键修改 AUDIT。默认空实现；发布/下架另走 Outbox {@code work.published}/{@code work.unpublished}。
 */
public interface WorkAuditPort {

    void record(long workId, String action, String reason, String actor);
}
