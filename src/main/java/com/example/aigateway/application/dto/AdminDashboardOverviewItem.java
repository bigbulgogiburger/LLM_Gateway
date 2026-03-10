package com.example.aigateway.application.dto;

import java.util.List;

public record AdminDashboardOverviewItem(
        String tenantId,
        int sinceHours,
        long activeClientCount,
        long activeUserCount,
        long blockedRequestCount,
        int anomalyCount,
        double successRate,
        double blockedRate,
        double costPer1kTokens,
        String lastRequestAt,
        AdminUsageMetricsItem usage,
        List<AdminUsageMetricsItem.DimensionBreakdownItem> topProviders,
        List<AdminUsageMetricsItem.DimensionBreakdownItem> topModels,
        List<AdminUsageMetricsItem.DimensionBreakdownItem> topClients,
        List<AdminUsageMetricsItem.DimensionBreakdownItem> topUsers,
        List<AdminUsageMetricsItem.DimensionBreakdownItem> topBlockedReasons,
        List<RecentBlockedItem> recentBlocked,
        List<RecentCostlyItem> recentCostly
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

    public record RecentCostlyItem(
            String requestId,
            String clientId,
            String userId,
            String provider,
            String model,
            long totalTokens,
            double costUsd,
            String createdAt
    ) {
    }
}
