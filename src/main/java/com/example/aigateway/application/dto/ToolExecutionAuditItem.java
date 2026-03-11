package com.example.aigateway.application.dto;

public record ToolExecutionAuditItem(
        String callId,
        String toolName,
        String argumentsSummary,
        long durationMillis,
        int attempts,
        boolean truncated
) {
}
