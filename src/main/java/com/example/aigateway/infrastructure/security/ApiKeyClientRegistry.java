package com.example.aigateway.infrastructure.security;

import com.example.aigateway.infrastructure.config.GatewaySecurityProperties;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class ApiKeyClientRegistry {

    private final GatewaySecurityProperties properties;

    public ApiKeyClientRegistry(GatewaySecurityProperties properties) {
        this.properties = properties;
    }

    public String headerName() {
        return properties.apiKeyHeader();
    }

    public Optional<GatewaySecurityProperties.ClientProperties> findByApiKey(String apiKey) {
        return properties.clients().stream()
                .filter(client -> client.apiKey().equals(apiKey))
                .findFirst();
    }

    public boolean enabled() {
        return properties.enabled();
    }
}
