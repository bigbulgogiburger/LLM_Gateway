package com.example.aigateway.application.dto;

import com.example.aigateway.infrastructure.config.GatewayTenantPolicyProperties;
import java.time.Instant;

public record TenantPolicyOverrideHistoryItem(
        long version,
        String action,
        Long sourceVersion,
        Instant createdAt,
        GatewayTenantPolicyProperties.TenantPolicyOverride override
) {
}
