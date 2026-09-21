package com.koujunxin.notification.outbox.consume;

/**
 * 消费者幂等：以 event_id + channel 去重。
 * <p>本地副作用（如站内信）在 DONE 后不重复执行。外部 SMS 仍是至少一次：
 * 崩溃重投可能再次调用提供方，必须把 eventId 传给支持幂等的提供方，不得声称 exactly-once。
 */
public interface ConsumerIdempotencyPort {

    /**
     * 原子领取：插入新 PROCESSING，或回收已过期的 PROCESSING。
     *
     * @return true 表示本次占有，调用方应执行副作用；false 表示 DONE 或租约未到期，应跳过。
     */
    boolean tryBegin(String eventId, String channel);

    void complete(String eventId, String channel);

    /**
     * 处理失败时释放 PROCESSING 占位，允许后续 tryBegin 重新领取。DONE 行不会被删除。
     */
    void abandon(String eventId, String channel);

    boolean alreadyCompleted(String eventId, String channel);
}
