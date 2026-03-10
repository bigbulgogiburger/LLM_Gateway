package com.example.aigateway.application.service;

import com.example.aigateway.application.dto.AdminUsageMetricsItem;
import com.example.aigateway.infrastructure.audit.AuditLogEntity;
import com.example.aigateway.infrastructure.audit.AuditLogRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class AdminUsageMetricsService {

    private final AuditLogRepository auditLogRepository;

    public AdminUsageMetricsService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    public AdminUsageMetricsItem summarize(String tenantId, String provider, String model, String tool, Integer sinceHours) {
        int effectiveSinceHours = sinceHours == null ? 24 : sinceHours;
        Instant now = Instant.now();
        Instant since = now.minus(effectiveSinceHours, ChronoUnit.HOURS);
        Instant previousSince = since.minus(effectiveSinceHours, ChronoUnit.HOURS);
        String bucketUnit = effectiveSinceHours <= 48 ? "hour" : "day";
        List<AuditLogEntity> currentWindow = filterEntities(
                auditLogRepository.findTop500ByTenantIdAndCreatedAtAfterOrderByCreatedAtDesc(tenantId, since),
                provider,
                model,
                tool
        );
        List<AuditLogEntity> previousWindow = filterEntities(
                auditLogRepository.findTop500ByTenantIdAndCreatedAtAfterOrderByCreatedAtDesc(tenantId, previousSince).stream()
                        .filter(entity -> entity.getCreatedAt().isBefore(since))
                        .toList(),
                provider,
                model,
                tool
        );
        MetricsSummary currentSummary = summarizeMetrics(currentWindow);
        MetricsSummary previousSummary = summarizeMetrics(previousWindow);
        List<AuditLogEntity> filtered = currentWindow.stream()
                .toList();

        List<AdminUsageMetricsItem.ProviderUsageBreakdownItem> breakdown = filtered.stream()
                .collect(Collectors.groupingBy(entity -> safeLower(entity.getProvider()) + "|" + safeLower(entity.getModel())))
                .entrySet().stream()
                .map(entry -> {
                    List<AuditLogEntity> items = entry.getValue();
                    AuditLogEntity first = items.get(0);
                    return new AdminUsageMetricsItem.ProviderUsageBreakdownItem(
                            first.getProvider(),
                            first.getModel(),
                            items.size(),
                            items.stream().map(AuditLogEntity::getTotalTokens).filter(Objects::nonNull).mapToLong(Integer::longValue).sum(),
                            items.stream().map(AuditLogEntity::getCostUsd).filter(Objects::nonNull).mapToDouble(Double::doubleValue).sum()
                    );
                })
                .sorted(Comparator.comparingLong(AdminUsageMetricsItem.ProviderUsageBreakdownItem::requests).reversed())
                .toList();

        List<AdminUsageMetricsItem.UsageTimeSeriesBucketItem> timeSeries = filtered.stream()
                .collect(Collectors.groupingBy(entity -> bucketStart(entity.getCreatedAt(), bucketUnit)))
                .entrySet().stream()
                .map(entry -> {
                    List<AuditLogEntity> items = entry.getValue();
                    long bucketSuccessCount = items.stream()
                            .filter(entity -> "SUCCESS".equalsIgnoreCase(entity.getStatus()))
                            .count();
                    long bucketBlockedCount = items.stream()
                            .filter(entity -> "BLOCKED".equalsIgnoreCase(entity.getStatus()))
                            .count();
                    return new AdminUsageMetricsItem.UsageTimeSeriesBucketItem(
                            entry.getKey().toString(),
                            items.size(),
                            bucketSuccessCount,
                            bucketBlockedCount,
                            items.stream()
                                    .map(AuditLogEntity::getTotalTokens)
                                    .filter(Objects::nonNull)
                                    .mapToLong(Integer::longValue)
                                    .sum(),
                            items.stream()
                                    .map(AuditLogEntity::getCostUsd)
                                    .filter(Objects::nonNull)
                                    .mapToDouble(Double::doubleValue)
                                    .sum()
                    );
                })
                .sorted(Comparator.comparing(AdminUsageMetricsItem.UsageTimeSeriesBucketItem::bucketStart))
                .toList();

        List<AdminUsageMetricsItem.DimensionBreakdownItem> providerBreakdown = buildDimensionBreakdown(
                filtered,
                AuditLogEntity::getProvider
        );
        List<AdminUsageMetricsItem.DimensionBreakdownItem> modelBreakdown = buildDimensionBreakdown(
                filtered,
                entity -> entity.getModel() == null ? "unknown" : entity.getModel()
        );
        List<AdminUsageMetricsItem.DimensionBreakdownItem> clientBreakdown = buildDimensionBreakdown(
                filtered,
                AuditLogEntity::getClientId
        );
        List<AdminUsageMetricsItem.DimensionBreakdownItem> userBreakdown = buildDimensionBreakdown(
                filtered,
                AuditLogEntity::getUserId
        );
        List<AdminUsageMetricsItem.DimensionBreakdownItem> toolBreakdown = filtered.stream()
                .flatMap(entity -> extractTools(entity.getToolNames()).stream()
                        .map(toolName -> new ToolMetricSource(
                                toolName,
                                entity.getStatus(),
                                entity.getTotalTokens(),
                                entity.getCostUsd()
                        )))
                .collect(Collectors.groupingBy(ToolMetricSource::toolName))
                .entrySet().stream()
                .map(entry -> {
                    List<ToolMetricSource> items = entry.getValue();
                    return new AdminUsageMetricsItem.DimensionBreakdownItem(
                            entry.getKey(),
                            items.size(),
                            items.stream().filter(item -> "SUCCESS".equalsIgnoreCase(item.status())).count(),
                            items.stream().filter(item -> "BLOCKED".equalsIgnoreCase(item.status())).count(),
                            items.stream().map(ToolMetricSource::totalTokens).filter(Objects::nonNull).mapToLong(Integer::longValue).sum(),
                            items.stream().map(ToolMetricSource::costUsd).filter(Objects::nonNull).mapToDouble(Double::doubleValue).sum()
                    );
                })
                .sorted(Comparator.comparingLong(AdminUsageMetricsItem.DimensionBreakdownItem::requests).reversed()
                        .thenComparing(AdminUsageMetricsItem.DimensionBreakdownItem::key))
                .toList();
        List<AdminUsageMetricsItem.DimensionBreakdownItem> blockedReasonBreakdown = buildDimensionBreakdown(
                filtered.stream()
                        .filter(entity -> "BLOCKED".equalsIgnoreCase(entity.getStatus()))
                        .toList(),
                entity -> entity.getRuleCodes() == null || entity.getRuleCodes().isBlank() ? "blocked_unknown" : firstCode(entity.getRuleCodes())
        );
        List<AdminUsageMetricsItem.DimensionBreakdownItem> ruleCodeBreakdown = filtered.stream()
                .flatMap(entity -> extractCodes(entity.getRuleCodes()).stream()
                        .map(ruleCode -> new ToolMetricSource(
                                ruleCode,
                                entity.getStatus(),
                                entity.getTotalTokens(),
                                entity.getCostUsd()
                        )))
                .collect(Collectors.groupingBy(ToolMetricSource::toolName))
                .entrySet().stream()
                .map(entry -> {
                    List<ToolMetricSource> items = entry.getValue();
                    return new AdminUsageMetricsItem.DimensionBreakdownItem(
                            entry.getKey(),
                            items.size(),
                            items.stream().filter(item -> "SUCCESS".equalsIgnoreCase(item.status())).count(),
                            items.stream().filter(item -> "BLOCKED".equalsIgnoreCase(item.status())).count(),
                            items.stream().map(ToolMetricSource::totalTokens).filter(Objects::nonNull).mapToLong(Integer::longValue).sum(),
                            items.stream().map(ToolMetricSource::costUsd).filter(Objects::nonNull).mapToDouble(Double::doubleValue).sum()
                    );
                })
                .sorted(Comparator.comparingLong(AdminUsageMetricsItem.DimensionBreakdownItem::requests).reversed()
                        .thenComparing(AdminUsageMetricsItem.DimensionBreakdownItem::key))
                .toList();
        AdminUsageMetricsItem.ComparisonItem comparison = new AdminUsageMetricsItem.ComparisonItem(
                previousSummary.totalRequests(),
                previousSummary.successCount(),
                previousSummary.blockedCount(),
                previousSummary.totalTokens(),
                previousSummary.totalCostUsd(),
                currentSummary.totalRequests() - previousSummary.totalRequests(),
                currentSummary.successCount() - previousSummary.successCount(),
                currentSummary.blockedCount() - previousSummary.blockedCount(),
                currentSummary.totalTokens() - previousSummary.totalTokens(),
                currentSummary.totalCostUsd() - previousSummary.totalCostUsd()
        );
        List<AdminUsageMetricsItem.AnomalyFlagItem> anomalyFlags = detectAnomalies(currentSummary, previousSummary);

        return new AdminUsageMetricsItem(
                tenantId,
                effectiveSinceHours,
                bucketUnit,
                currentSummary.totalRequests(),
                currentSummary.successCount(),
                currentSummary.blockedCount(),
                currentSummary.totalToolCalls(),
                currentSummary.totalInputTokens(),
                currentSummary.totalOutputTokens(),
                currentSummary.totalTokens(),
                currentSummary.totalCostUsd(),
                currentSummary.averageTokensPerRequest(),
                currentSummary.averageCostUsdPerRequest(),
                breakdown,
                providerBreakdown,
                modelBreakdown,
                clientBreakdown,
                userBreakdown,
                toolBreakdown,
                blockedReasonBreakdown,
                ruleCodeBreakdown,
                comparison,
                anomalyFlags,
                timeSeries
        );
    }

    private List<AuditLogEntity> filterEntities(List<AuditLogEntity> entities, String provider, String model, String tool) {
        return entities.stream()
                .filter(entity -> provider == null || provider.isBlank() || provider.equalsIgnoreCase(entity.getProvider()))
                .filter(entity -> model == null || model.isBlank() || Objects.equals(model.toLowerCase(), safeLower(entity.getModel())))
                .filter(entity -> tool == null || tool.isBlank() || containsTool(entity.getToolNames(), tool))
                .toList();
    }

    private String safeLower(String value) {
        return value == null ? "" : value.toLowerCase();
    }

    private boolean containsTool(String toolNames, String tool) {
        return extractTools(toolNames).stream()
                .anyMatch(value -> value.equalsIgnoreCase(tool));
    }

    private List<String> extractTools(String toolNames) {
        if (toolNames == null || toolNames.isBlank()) {
            return List.of();
        }
        return List.of(toolNames.split(",")).stream()
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toList();
    }

    private Instant bucketStart(Instant createdAt, String bucketUnit) {
        if ("day".equals(bucketUnit)) {
            LocalDate date = createdAt.atZone(ZoneOffset.UTC).toLocalDate();
            return date.atStartOfDay().toInstant(ZoneOffset.UTC);
        }
        return createdAt.truncatedTo(ChronoUnit.HOURS);
    }

    private List<String> extractCodes(String ruleCodes) {
        if (ruleCodes == null || ruleCodes.isBlank()) {
            return List.of();
        }
        return List.of(ruleCodes.split(",")).stream()
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toList();
    }

    private String firstCode(String ruleCodes) {
        return extractCodes(ruleCodes).stream().findFirst().orElse("blocked_unknown");
    }

    private List<AdminUsageMetricsItem.DimensionBreakdownItem> buildDimensionBreakdown(
            List<AuditLogEntity> filtered,
            java.util.function.Function<AuditLogEntity, String> keyExtractor
    ) {
        return filtered.stream()
                .collect(Collectors.groupingBy(entity -> safeDimensionKey(keyExtractor.apply(entity))))
                .entrySet().stream()
                .map(entry -> {
                    List<AuditLogEntity> items = entry.getValue();
                    return new AdminUsageMetricsItem.DimensionBreakdownItem(
                            entry.getKey(),
                            items.size(),
                            items.stream().filter(entity -> "SUCCESS".equalsIgnoreCase(entity.getStatus())).count(),
                            items.stream().filter(entity -> "BLOCKED".equalsIgnoreCase(entity.getStatus())).count(),
                            items.stream().map(AuditLogEntity::getTotalTokens).filter(Objects::nonNull).mapToLong(Integer::longValue).sum(),
                            items.stream().map(AuditLogEntity::getCostUsd).filter(Objects::nonNull).mapToDouble(Double::doubleValue).sum()
                    );
                })
                .sorted(Comparator.comparingLong(AdminUsageMetricsItem.DimensionBreakdownItem::requests).reversed()
                        .thenComparing(AdminUsageMetricsItem.DimensionBreakdownItem::key))
                .toList();
    }

    private String safeDimensionKey(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }

    private List<AdminUsageMetricsItem.AnomalyFlagItem> detectAnomalies(MetricsSummary currentSummary, MetricsSummary previousSummary) {
        java.util.ArrayList<AdminUsageMetricsItem.AnomalyFlagItem> flags = new java.util.ArrayList<>();

        if (currentSummary.totalRequests() >= 10 && currentSummary.totalRequests() >= Math.max(1, previousSummary.totalRequests()) * 2) {
            flags.add(new AdminUsageMetricsItem.AnomalyFlagItem(
                    "REQUEST_SPIKE",
                    "warning",
                    "최근 요청 수가 직전 구간 대비 2배 이상 증가했습니다."
            ));
        }
        if (currentSummary.blockedCount() >= 1 && currentSummary.blockedCount() > previousSummary.blockedCount()) {
            flags.add(new AdminUsageMetricsItem.AnomalyFlagItem(
                    "BLOCKED_SPIKE",
                    "warning",
                    "차단 요청 수가 직전 구간보다 증가했습니다."
            ));
        }
        if (currentSummary.totalCostUsd() >= 0.01d && currentSummary.totalCostUsd() >= Math.max(0.000001d, previousSummary.totalCostUsd()) * 2) {
            flags.add(new AdminUsageMetricsItem.AnomalyFlagItem(
                    "COST_SPIKE",
                    "critical",
                    "최근 비용이 직전 구간 대비 2배 이상 증가했습니다."
            ));
        }
        return List.copyOf(flags);
    }

    private record ToolMetricSource(
            String toolName,
            String status,
            Integer totalTokens,
            Double costUsd
    ) {
    }

    private MetricsSummary summarizeMetrics(List<AuditLogEntity> entities) {
        long successCount = entities.stream().filter(entity -> "SUCCESS".equalsIgnoreCase(entity.getStatus())).count();
        long blockedCount = entities.stream().filter(entity -> "BLOCKED".equalsIgnoreCase(entity.getStatus())).count();
        long totalToolCalls = entities.stream().mapToLong(AuditLogEntity::getToolCallCount).sum();
        long totalInputTokens = entities.stream().map(AuditLogEntity::getInputTokens).filter(Objects::nonNull).mapToLong(Integer::longValue).sum();
        long totalOutputTokens = entities.stream().map(AuditLogEntity::getOutputTokens).filter(Objects::nonNull).mapToLong(Integer::longValue).sum();
        long totalTokens = entities.stream().map(AuditLogEntity::getTotalTokens).filter(Objects::nonNull).mapToLong(Integer::longValue).sum();
        double totalCostUsd = entities.stream().map(AuditLogEntity::getCostUsd).filter(Objects::nonNull).mapToDouble(Double::doubleValue).sum();
        double averageTokensPerRequest = entities.isEmpty() ? 0.0d : (double) totalTokens / entities.size();
        double averageCostUsdPerRequest = entities.isEmpty() ? 0.0d : totalCostUsd / entities.size();
        return new MetricsSummary(
                entities.size(),
                successCount,
                blockedCount,
                totalToolCalls,
                totalInputTokens,
                totalOutputTokens,
                totalTokens,
                totalCostUsd,
                averageTokensPerRequest,
                averageCostUsdPerRequest
        );
    }

    private record MetricsSummary(
            long totalRequests,
            long successCount,
            long blockedCount,
            long totalToolCalls,
            long totalInputTokens,
            long totalOutputTokens,
            long totalTokens,
            double totalCostUsd,
            double averageTokensPerRequest,
            double averageCostUsdPerRequest
    ) {
    }
}
