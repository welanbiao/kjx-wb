package com.koujunxin.log.metrics;

import com.koujunxin.log.api.port.ObservabilityMetricsPort;

public final class NoOpObservabilityMetrics implements ObservabilityMetricsPort {

    @Override
    public void recordOutboxBacklog(long pendingOrProcessing) {
        // no-op
    }

    @Override
    public void recordOutboxDead(long dead) {
        // no-op
    }

    @Override
    public void recordPublishError() {
        // no-op
    }

    @Override
    public void recordProjectionError() {
        // no-op
    }

    @Override
    public void recordCost(String model, double amount) {
        // no-op
    }
}
