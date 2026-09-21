package com.koujunxin.notification.outbox.consume;

/**
 * 消费者幂等：以 event_id + channel 去重。至少一次投递下只产生一个消费结果。
 */
public interface ConsumerIdempotencyPort {

    /**
     * @return true 表示首次占有，调用方应执行副作用；false 表示已消费，应跳过。
     */
    boolean tryBegin(String eventId, String channel);

    void complete(String eventId, String channel);

    /**
     * 处理失败时释放占位，允许后续重试。
     */
    void abandon(String eventId, String channel);

    boolean alreadyCompleted(String eventId, String channel);
}
