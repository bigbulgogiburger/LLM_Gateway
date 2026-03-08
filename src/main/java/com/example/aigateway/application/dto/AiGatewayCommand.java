package com.example.aigateway.application.dto;

public record AiGatewayCommand(
        String requestId,
        String tenantId,
        String clientId,
        String userId,
        String role,
        String provider,
        String prompt
) {
}
