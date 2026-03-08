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
        Instant since = Instant.now().minus(effectiveSinceHours, ChronoUnit.HOURS);
        String bucketUnit = effectiveSinceHours <= 48 ? "hour" : "day";
        List<AuditLogEntity> filtered = auditLogRepository.findTop500ByTenantIdAndCreatedAtAfterOrderByCreatedAtDesc(tenantId, since).stream()
                .filter(entity -> provider == null || provider.isBlank() || provider.equalsIgnoreCase(entity.getProvider()))
                .filter(entity -> model == null || model.isBlank() || Objects.equals(model.toLowerCase(), safeLower(entity.getModel())))
                .filter(entity -> tool == null || tool.isBlank() || containsTool(entity.getToolNames(), tool))
                .toList();

        long successCount = filtered.stream().filter(entity -> "SUCCESS".equalsIgnoreCase(entity.getStatus())).count();
        long blockedCount = filtered.stream().filter(entity -> "BLOCKED".equalsIgnoreCase(entity.getStatus())).count();
        long totalToolCalls = filtered.stream().mapToLong(AuditLogEntity::getToolCallCount).sum();
        long totalInputTokens = filtered.stream().map(AuditLogEntity::getInputTokens).filter(Objects::nonNull).mapToLong(Integer::longValue).sum();
        long totalOutputTokens = filtered.stream().map(AuditLogEntity::getOutputTokens).filter(Objects::nonNull).mapToLong(Integer::longValue).sum();
        long totalTokens = filtered.stream().map(AuditLogEntity::getTotalTokens).filter(Objects::nonNull).mapToLong(Integer::longValue).sum();
        double totalCostUsd = filtered.stream().map(AuditLogEntity::getCostUsd).filter(Objects::nonNull).mapToDouble(Double::doubleValue).sum();

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

        return new AdminUsageMetricsItem(
                tenantId,
                effectiveSinceHours,
                bucketUnit,
                filtered.size(),
                successCount,
                blockedCount,
                totalToolCalls,
                totalInputTokens,
                totalOutputTokens,
                totalTokens,
                totalCostUsd,
                breakdown,
                timeSeries
        );
    }

    private String safeLower(String value) {
        return value == null ? "" : value.toLowerCase();
    }

    private boolean containsTool(String toolNames, String tool) {
        if (toolNames == null || toolNames.isBlank()) {
            return false;
        }
        return List.of(toolNames.split(",")).stream()
                .map(String::trim)
                .anyMatch(value -> value.equalsIgnoreCase(tool));
    }

    private Instant bucketStart(Instant createdAt, String bucketUnit) {
        if ("day".equals(bucketUnit)) {
            LocalDate date = createdAt.atZone(ZoneOffset.UTC).toLocalDate();
            return date.atStartOfDay().toInstant(ZoneOffset.UTC);
        }
        return createdAt.truncatedTo(ChronoUnit.HOURS);
    }
}
