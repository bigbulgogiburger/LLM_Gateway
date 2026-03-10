package com.example.aigateway.application.dto;

import java.util.List;

public record AdminUsageMetricsItem(
        String tenantId,
        int sinceHours,
        String bucketUnit,
        long totalRequests,
        long successCount,
        long blockedCount,
        long totalToolCalls,
        long totalInputTokens,
        long totalOutputTokens,
        long totalTokens,
        double totalCostUsd,
        double averageTokensPerRequest,
        double averageCostUsdPerRequest,
        List<ProviderUsageBreakdownItem> breakdown,
        List<DimensionBreakdownItem> providerBreakdown,
        List<DimensionBreakdownItem> modelBreakdown,
        List<DimensionBreakdownItem> clientBreakdown,
        List<DimensionBreakdownItem> userBreakdown,
        List<DimensionBreakdownItem> toolBreakdown,
        List<DimensionBreakdownItem> blockedReasonBreakdown,
        List<DimensionBreakdownItem> ruleCodeBreakdown,
        List<UsageTimeSeriesBucketItem> timeSeries
) {
    public record ProviderUsageBreakdownItem(
            String provider,
            String model,
            long requests,
            long totalTokens,
            double totalCostUsd
    ) {
    }

    public record UsageTimeSeriesBucketItem(
            String bucketStart,
            long requests,
            long successCount,
            long blockedCount,
            long totalTokens,
            double totalCostUsd
    ) {
    }

    public record DimensionBreakdownItem(
            String key,
            long requests,
            long successCount,
            long blockedCount,
            long totalTokens,
            double totalCostUsd
    ) {
    }
}
