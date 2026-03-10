package com.example.aigateway.application.dto;

import java.util.List;

public record AdminDashboardOverviewItem(
        String tenantId,
        int sinceHours,
        long activeClientCount,
        long activeUserCount,
        long blockedRequestCount,
        int anomalyCount,
        AdminUsageMetricsItem usage,
        List<RecentBlockedItem> recentBlocked
) {
    public record RecentBlockedItem(
            String requestId,
            String clientId,
            String userId,
            String provider,
            String model,
            String reasonCode,
            int toolCallCount,
            long totalTokens,
            double costUsd,
            String createdAt
    ) {
    }
}
