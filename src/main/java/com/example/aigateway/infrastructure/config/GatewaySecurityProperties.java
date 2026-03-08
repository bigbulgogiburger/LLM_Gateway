package com.example.aigateway.infrastructure.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gateway.security")
public record GatewaySecurityProperties(
        boolean enabled,
        String apiKeyHeader,
        List<ClientProperties> clients
) {
    public record ClientProperties(
            String clientId,
            String tenantId,
            String apiKey,
            String role,
            int requestsPerMinute,
            int dailyRequestQuota,
            int dailyTokenQuota,
            List<String> allowedProviders
    ) {
    }
}
