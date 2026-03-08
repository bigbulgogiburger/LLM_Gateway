package com.example.aigateway.application.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import org.springframework.stereotype.Service;

@Service
public class GatewayMetricsService {

    private final MeterRegistry meterRegistry;

    public GatewayMetricsService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void recordOutcome(String provider, String outcome, String tenantId) {
        Counter.builder("gateway.requests.total")
                .tag("provider", provider)
                .tag("outcome", outcome)
                .tag("tenant", tenantId)
                .register(meterRegistry)
                .increment();
    }

    public void recordLatency(String provider, String outcome, String tenantId, long elapsedMillis) {
        Timer.builder("gateway.requests.latency")
                .tag("provider", provider)
                .tag("outcome", outcome)
                .tag("tenant", tenantId)
                .register(meterRegistry)
                .record(Duration.ofMillis(elapsedMillis));
    }
}
