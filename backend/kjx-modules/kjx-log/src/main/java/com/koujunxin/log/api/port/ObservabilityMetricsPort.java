package com.koujunxin.log.api.port;

/**
 * 可观测指标。无 MeterRegistry 时降级为空操作，不得阻止启动。
 */
public interface ObservabilityMetricsPort {

    void recordOutboxBacklog(long pendingOrProcessing);

    void recordOutboxDead(long dead);

    void recordPublishError();

    void recordProjectionError();

    void recordCost(String model, double amount);
}
