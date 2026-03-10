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
        int healthScore = calculateHealthScore(usage, blockedRate);
        String alertSeverity = calculateAlertSeverity(usage);
        boolean staleTenant = recentLogs.isEmpty();
        AdminDashboardOverviewItem.HotspotItem toolCostHotspot = usage.toolBreakdown().stream()
                .max(Comparator.comparing(AdminUsageMetricsItem.DimensionBreakdownItem::totalCostUsd)
                        .thenComparing(AdminUsageMetricsItem.DimensionBreakdownItem::key))
                .map(item -> new AdminDashboardOverviewItem.HotspotItem(item.key(), item.totalTokens(), item.totalCostUsd()))
                .orElse(null);
        AdminDashboardOverviewItem.RecommendationItem providerSwitchRecommendation = buildProviderRecommendation(usage, costPer1kTokens);
        AdminDashboardOverviewItem.ActorSummaryItem dominantBlockedClient = buildBlockedActorSummary(recentLogs, true);
        AdminDashboardOverviewItem.ActorSummaryItem dominantBlockedUser = buildBlockedActorSummary(recentLogs, false);
        AdminDashboardOverviewItem.PeakHourItem peakHour = usage.timeSeries().stream()
                .max(Comparator.comparing(AdminUsageMetricsItem.UsageTimeSeriesBucketItem::requests)
                        .thenComparing(AdminUsageMetricsItem.UsageTimeSeriesBucketItem::totalTokens))
                .map(item -> new AdminDashboardOverviewItem.PeakHourItem(
                        item.bucketStart(),
                        item.requests(),
                        item.totalTokens()
                ))
                .orElse(null);
        List<AdminDashboardOverviewItem.RecentBlockedItem> recentFailed = recentBlocked;
        List<AdminDashboardOverviewItem.RecentBlockedItem> recentGuardrailHits = recentBlocked.stream()
                .filter(item -> !"blocked_unknown".equals(item.reasonCode()))
                .toList();

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
                healthScore,
                alertSeverity,
                staleTenant,
                lastRequestAt,
                usage,
                limitBreakdown(usage.providerBreakdown(), 3),
                limitBreakdown(usage.modelBreakdown(), 3),
                limitBreakdown(usage.clientBreakdown(), 3),
                limitBreakdown(usage.userBreakdown(), 3),
                limitBreakdown(usage.blockedReasonBreakdown(), 3),
                toolCostHotspot,
                providerSwitchRecommendation,
                dominantBlockedClient,
                dominantBlockedUser,
                peakHour,
                recentBlocked,
                recentFailed,
                recentGuardrailHits,
                recentCostly
        );
    }

    private int calculateHealthScore(AdminUsageMetricsItem usage, double blockedRate) {
        int score = 100;
        score -= usage.anomalyFlags().size() * 15;
        score -= (int) Math.round(blockedRate);
        if (usage.totalCostUsd() > 0.01d) {
            score -= 10;
        }
        return Math.max(0, score);
    }

    private String calculateAlertSeverity(AdminUsageMetricsItem usage) {
        boolean hasCritical = usage.anomalyFlags().stream().anyMatch(flag -> "critical".equalsIgnoreCase(flag.severity()));
        if (hasCritical) {
            return "critical";
        }
        if (!usage.anomalyFlags().isEmpty()) {
            return "warning";
        }
        return "normal";
    }

    private AdminDashboardOverviewItem.RecommendationItem buildProviderRecommendation(
            AdminUsageMetricsItem usage,
            double costPer1kTokens
    ) {
        if (costPer1kTokens > 0.03d) {
            return new AdminDashboardOverviewItem.RecommendationItem(
                    "REVIEW_PROVIDER_MIX",
                    "비용이 높은 편입니다. mock 또는 더 저렴한 provider/model 조합 검토가 필요합니다."
            );
        }
        if (usage.blockedCount() > 0) {
            return new AdminDashboardOverviewItem.RecommendationItem(
                    "REVIEW_GUARDRAILS",
                    "차단 요청이 발생했습니다. 입력 정책과 클라이언트 사용 패턴을 점검하세요."
            );
        }
        return null;
    }

    private AdminDashboardOverviewItem.ActorSummaryItem buildBlockedActorSummary(List<AuditLogEntity> recentLogs, boolean client) {
        return recentLogs.stream()
                .filter(entity -> "BLOCKED".equalsIgnoreCase(entity.getStatus()))
                .collect(java.util.stream.Collectors.groupingBy(entity -> client ? entity.getClientId() : entity.getUserId(), java.util.stream.Collectors.counting()))
                .entrySet().stream()
                .filter(entry -> entry.getKey() != null)
                .max(java.util.Map.Entry.<String, Long>comparingByValue().thenComparing(java.util.Map.Entry.comparingByKey()))
                .map(entry -> new AdminDashboardOverviewItem.ActorSummaryItem(entry.getKey(), entry.getValue()))
                .orElse(null);
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
