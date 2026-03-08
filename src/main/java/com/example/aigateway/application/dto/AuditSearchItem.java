package com.example.aigateway.application.dto;

public record AuditSearchItem(
        String requestId,
        String tenantId,
        String clientId,
        String provider,
        String status
) {
}
