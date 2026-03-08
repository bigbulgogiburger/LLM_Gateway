package com.example.aigateway.infrastructure.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gateway.providers.resilience")
public record ProviderResilienceProperties(
        int retryAttempts,
        Duration retryBackoff,
        int circuitBreakerSlidingWindowSize,
        int circuitBreakerMinimumCalls,
        float circuitBreakerFailureRateThreshold,
        Duration circuitBreakerOpenDuration
) {
}
