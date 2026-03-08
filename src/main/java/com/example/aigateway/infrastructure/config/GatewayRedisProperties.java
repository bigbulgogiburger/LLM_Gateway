package com.example.aigateway.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gateway.redis")
public record GatewayRedisProperties(
        boolean enabled,
        String namespace
) {
}
