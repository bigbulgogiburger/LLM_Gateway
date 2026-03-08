package com.example.aigateway.domain.security;

import java.security.Principal;
import java.util.List;

public record GatewayPrincipal(
        String clientId,
        String tenantId,
        String role,
        int dailyRequestQuota,
        int dailyTokenQuota,
        List<String> allowedProviders,
        List<String> manageableTenants,
        List<String> allowedTools
) implements Principal {

    @Override
    public String getName() {
        return clientId;
    }

    public boolean allowsProvider(String provider) {
        return allowedProviders.stream().anyMatch(allowed -> allowed.equalsIgnoreCase(provider));
    }

    public boolean canManageTenant(String requestedTenantId) {
        if (requestedTenantId == null || requestedTenantId.isBlank()) {
            return false;
        }
        if (tenantId.equalsIgnoreCase(requestedTenantId)) {
            return true;
        }
        if (manageableTenants == null) {
            return false;
        }
        return manageableTenants.stream().anyMatch(allowedTenant -> allowedTenant.equalsIgnoreCase(requestedTenantId));
    }

    public boolean allowsTool(String toolName) {
        if (toolName == null || toolName.isBlank() || allowedTools == null) {
            return false;
        }
        return allowedTools.stream().anyMatch(allowedTool -> allowedTool.equalsIgnoreCase(toolName));
    }
}
