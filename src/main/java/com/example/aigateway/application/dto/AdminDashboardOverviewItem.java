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
        int healthScore,
        String alertSeverity,
        boolean staleTenant,
        String lastRequestAt,
        AdminUsageMetricsItem usage,
        List<AdminUsageMetricsItem.DimensionBreakdownItem> topProviders,
        List<AdminUsageMetricsItem.DimensionBreakdownItem> topModels,
        List<AdminUsageMetricsItem.DimensionBreakdownItem> topClients,
        List<AdminUsageMetricsItem.DimensionBreakdownItem> topUsers,
        List<AdminUsageMetricsItem.DimensionBreakdownItem> topBlockedReasons,
        HotspotItem toolCostHotspot,
        RecommendationItem providerSwitchRecommendation,
        ActorSummaryItem dominantBlockedClient,
        ActorSummaryItem dominantBlockedUser,
        PeakHourItem peakHour,
        List<FailureSampleItem> providerFailureSamples,
        List<FailureSampleItem> modelFailureSamples,
        List<FailureSampleItem> toolFailureSamples,
        List<TrendPointItem> blockedTrend,
        List<TrendPointItem> costTrend,
        List<TrendPointItem> guardrailTrend,
        List<RiskScoreItem> clientRiskScores,
        List<RiskScoreItem> userRiskScores,
        List<EfficiencyScoreItem> providerEfficiencyScores,
        List<EfficiencyScoreItem> modelEfficiencyScores,
        List<RecentBlockedItem> recentBlocked,
        List<RecentBlockedItem> recentFailed,
        List<RecentBlockedItem> recentGuardrailHits,
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

    public record HotspotItem(
            String key,
            long totalTokens,
            double totalCostUsd
    ) {
    }

    public record RecommendationItem(
            String code,
            String message
    ) {
    }

    public record ActorSummaryItem(
            String key,
            long blockedCount
    ) {
    }

    public record PeakHourItem(
            String bucketStart,
            long requests,
            long totalTokens
    ) {
    }

    public record FailureSampleItem(
            String key,
            String requestId,
            String reasonCode,
            String createdAt
    ) {
    }

    public record TrendPointItem(
            String key,
            long value
    ) {
    }

    public record RiskScoreItem(
            String key,
            int score
    ) {
    }

    public record EfficiencyScoreItem(
            String key,
            double score
    ) {
    }
}
