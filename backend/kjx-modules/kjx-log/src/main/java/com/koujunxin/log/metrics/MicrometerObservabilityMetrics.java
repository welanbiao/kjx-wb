package com.koujunxin.log.metrics;

import com.koujunxin.log.api.port.ObservabilityMetricsPort;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

public final class MicrometerObservabilityMetrics implements ObservabilityMetricsPort {

    public static final String OUTBOX_BACKLOG = "kjx.outbox.backlog";
    public static final String OUTBOX_DEAD = "kjx.outbox.dead";
    public static final String OUTBOX_PUBLISH_ERRORS = "kjx.outbox.publish.errors";
    public static final String PROJECTION_ERRORS = "kjx.log.projection.errors";
    public static final String AI_COST = "kjx.ai.cost.amount";

    private final Counter publishErrors;
    private final Counter projectionErrors;
    private final MeterRegistry registry;

    public MicrometerObservabilityMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.publishErrors = registry.counter(OUTBOX_PUBLISH_ERRORS);
        this.projectionErrors = registry.counter(PROJECTION_ERRORS);
    }

    @Override
    public void recordOutboxBacklog(long pendingOrProcessing) {
        // 积压 Gauge 由 OutboxBacklogProbe 在刮取时查询。
    }

    @Override
    public void recordOutboxDead(long deadCount) {
        // 同上
    }

    @Override
    public void recordPublishError() {
        publishErrors.increment();
    }

    @Override
    public void recordProjectionError() {
        projectionErrors.increment();
    }

    @Override
    public void recordCost(String model, double amount) {
        String tag = model == null || model.isBlank() ? "unknown" : model;
        registry.counter(AI_COST, "model", tag).increment(Math.max(0, amount));
    }
}
