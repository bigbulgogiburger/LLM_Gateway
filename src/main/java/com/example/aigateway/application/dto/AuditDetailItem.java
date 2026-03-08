package com.example.aigateway.application.dto;

import java.time.Instant;
import java.util.List;

public record AuditDetailItem(
        String requestId,
        String tenantId,
        String clientId,
        String userId,
        String role,
        String provider,
        String model,
        String status,
        boolean passed,
        String aiVerdict,
        boolean outputPassed,
        boolean outputModified,
        List<String> ruleCodes,
        int toolCallCount,
        List<String> toolNames,
        Integer inputTokens,
        Integer outputTokens,
        Integer totalTokens,
        Double costUsd,
        long elapsedMillis,
        String promptSummary,
        Instant createdAt
) {
}
