package com.example.aigateway.infrastructure.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import com.example.aigateway.infrastructure.config.GatewaySecurityProperties;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "gateway.redis", name = "enabled", havingValue = "false", matchIfMissing = true)
public class ClientRateLimitService implements ClientRateLimiter {

    private final ConcurrentMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    @Override
    public RateLimitResult consume(GatewaySecurityProperties.ClientProperties client) {
        Bucket bucket = buckets.computeIfAbsent(client.clientId(), ignored -> Bucket.builder()
                .addLimit(Bandwidth.simple(client.requestsPerMinute(), Duration.ofMinutes(1)))
                .build());
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        if (probe.isConsumed()) {
            return RateLimitResult.permit();
        }
        return RateLimitResult.blocked(Math.max(1, probe.getNanosToWaitForRefill() / 1_000_000_000L));
    }
}
