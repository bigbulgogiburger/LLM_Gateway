package com.example.aigateway.application.dto;

public record ToolExecutionResult(
        String callId,
        String toolName,
        String argumentsSummary,
        String output,
        long durationMillis
) {
}
