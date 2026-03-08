package com.example.aigateway.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gateway.tools")
public record ToolExecutionProperties(
        int executionTimeoutMillis
) {
}
