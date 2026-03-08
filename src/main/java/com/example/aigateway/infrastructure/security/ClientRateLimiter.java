package com.example.aigateway.infrastructure.security;

import com.example.aigateway.infrastructure.config.GatewaySecurityProperties;

public interface ClientRateLimiter {
    RateLimitResult consume(GatewaySecurityProperties.ClientProperties client);
}
