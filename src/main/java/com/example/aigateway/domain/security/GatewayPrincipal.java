package com.example.aigateway.domain.security;

import java.security.Principal;
import java.util.List;

public record GatewayPrincipal(
        String clientId,
        String tenantId,
        String role,
        int dailyRequestQuota,
        int dailyTokenQuota,
        List<String> allowedProviders
) implements Principal {

    @Override
    public String getName() {
        return clientId;
    }

    public boolean allowsProvider(String provider) {
        return allowedProviders.stream().anyMatch(allowed -> allowed.equalsIgnoreCase(provider));
    }
}
