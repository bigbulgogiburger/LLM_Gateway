package com.example.aigateway.application.service;

import com.example.aigateway.application.dto.AdminDashboardOverviewItem;
import com.example.aigateway.application.dto.AdminUsageMetricsItem;
import com.example.aigateway.infrastructure.audit.AuditLogEntity;
import com.example.aigateway.infrastructure.audit.AuditLogRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;

@Service
public class AdminDashboardOverviewService {

    private final AdminUsageMetricsService adminUsageMetricsService;
    private final AuditLogRepository auditLogRepository;

    public AdminDashboardOverviewService(
            AdminUsageMetricsService adminUsageMetricsService,
            AuditLogRepository auditLogRepository
    ) {
        this.adminUsageMetricsService = adminUsageMetricsService;
        this.auditLogRepository = auditLogRepository;
    }

    public AdminDashboardOverviewItem getOverview(String tenantId, Integer sinceHours) {
        int effectiveSinceHours = sinceHours == null ? 24 : sinceHours;
        Instant since = Instant.now().minus(effectiveSinceHours, ChronoUnit.HOURS);
        List<AuditLogEntity> recentLogs = auditLogRepository.findTop500ByTenantIdAndCreatedAtAfterOrderByCreatedAtDesc(tenantId, since);
        AdminUsageMetricsItem usage = adminUsageMetricsService.summarize(tenantId, null, null, null, effectiveSinceHours);

        List<AdminDashboardOverviewItem.RecentBlockedItem> recentBlocked = recentLogs.stream()
                .filter(entity -> "BLOCKED".equalsIgnoreCase(entity.getStatus()))
                .sorted(Comparator.comparing(AuditLogEntity::getCreatedAt).reversed())
                .limit(5)
                .map(entity -> new AdminDashboardOverviewItem.RecentBlockedItem(
                        entity.getRequestId(),
                        entity.getClientId(),
                        entity.getUserId(),
                        entity.getProvider(),
                        entity.getModel(),
                        firstReasonCode(entity.getRuleCodes()),
                        entity.getToolCallCount(),
                        entity.getTotalTokens() == null ? 0 : entity.getTotalTokens(),
                        entity.getCostUsd() == null ? 0.0d : entity.getCostUsd(),
                        entity.getCreatedAt().toString()
                ))
                .toList();

        List<AdminDashboardOverviewItem.RecentCostlyItem> recentCostly = recentLogs.stream()
                .filter(entity -> entity.getCostUsd() != null)
                .sorted(Comparator.comparing(AuditLogEntity::getCostUsd, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(AuditLogEntity::getCreatedAt, Comparator.reverseOrder()))
                .limit(5)
                .map(entity -> new AdminDashboardOverviewItem.RecentCostlyItem(
                        entity.getRequestId(),
                        entity.getClientId(),
                        entity.getUserId(),
                        entity.getProvider(),
                        entity.getModel(),
                        entity.getTotalTokens() == null ? 0 : entity.getTotalTokens(),
                        entity.getCostUsd() == null ? 0.0d : entity.getCostUsd(),
                        entity.getCreatedAt().toString()
                ))
                .toList();

        double successRate = usage.totalRequests() == 0 ? 0.0d : ((double) usage.successCount() / usage.totalRequests()) * 100.0d;
        double blockedRate = usage.totalRequests() == 0 ? 0.0d : ((double) usage.blockedCount() / usage.totalRequests()) * 100.0d;
        double costPer1kTokens = usage.totalTokens() == 0 ? 0.0d : (usage.totalCostUsd() / usage.totalTokens()) * 1000.0d;
        String lastRequestAt = recentLogs.stream()
                .map(AuditLogEntity::getCreatedAt)
                .max(Instant::compareTo)
                .map(Instant::toString)
                .orElse(null);

        return new AdminDashboardOverviewItem(
                tenantId,
                effectiveSinceHours,
                recentLogs.stream().map(AuditLogEntity::getClientId).filter(Objects::nonNull).distinct().count(),
                recentLogs.stream().map(AuditLogEntity::getUserId).filter(Objects::nonNull).distinct().count(),
                recentBlocked.size(),
                usage.anomalyFlags().size(),
                successRate,
                blockedRate,
                costPer1kTokens,
                lastRequestAt,
                usage,
                limitBreakdown(usage.providerBreakdown(), 3),
                limitBreakdown(usage.modelBreakdown(), 3),
                limitBreakdown(usage.clientBreakdown(), 3),
                limitBreakdown(usage.userBreakdown(), 3),
                limitBreakdown(usage.blockedReasonBreakdown(), 3),
                recentBlocked,
                recentCostly
        );
    }

    private List<AdminUsageMetricsItem.DimensionBreakdownItem> limitBreakdown(
            List<AdminUsageMetricsItem.DimensionBreakdownItem> items,
            int limit
    ) {
        return items.stream().limit(limit).toList();
    }

    private String firstReasonCode(String ruleCodes) {
        if (ruleCodes == null || ruleCodes.isBlank()) {
            return "blocked_unknown";
        }
        return List.of(ruleCodes.split(",")).stream()
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .findFirst()
                .orElse("blocked_unknown");
    }
}
