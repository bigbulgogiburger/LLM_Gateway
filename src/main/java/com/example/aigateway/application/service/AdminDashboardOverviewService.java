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
        List<AdminDashboardOverviewItem.FailureSampleItem> providerFailureSamples = buildFailureSamples(
                recentLogs,
                AuditLogEntity::getProvider
        );
        List<AdminDashboardOverviewItem.FailureSampleItem> modelFailureSamples = buildFailureSamples(
                recentLogs,
                entity -> entity.getModel() == null ? "unknown" : entity.getModel()
        );
        List<AdminDashboardOverviewItem.FailureSampleItem> toolFailureSamples = recentLogs.stream()
                .filter(entity -> "BLOCKED".equalsIgnoreCase(entity.getStatus()))
                .flatMap(entity -> extractTools(entity.getToolNames()).stream()
                        .map(tool -> new AdminDashboardOverviewItem.FailureSampleItem(
                                tool,
                                entity.getRequestId(),
                                firstReasonCode(entity.getRuleCodes()),
                                entity.getCreatedAt().toString()
                        )))
                .limit(5)
                .toList();
        List<AdminDashboardOverviewItem.TrendPointItem> blockedTrend = usage.timeSeries().stream()
                .map(item -> new AdminDashboardOverviewItem.TrendPointItem(item.bucketStart(), item.blockedCount()))
                .toList();
        List<AdminDashboardOverviewItem.TrendPointItem> costTrend = usage.timeSeries().stream()
                .map(item -> new AdminDashboardOverviewItem.TrendPointItem(item.bucketStart(), Math.round(item.totalCostUsd() * 1_000_000)))
                .toList();
        List<AdminDashboardOverviewItem.TrendPointItem> guardrailTrend = usage.timeSeries().stream()
                .map(item -> new AdminDashboardOverviewItem.TrendPointItem(item.bucketStart(), item.blockedCount()))
                .toList();
        List<AdminDashboardOverviewItem.RiskScoreItem> clientRiskScores = buildRiskScores(recentLogs, true);
        List<AdminDashboardOverviewItem.RiskScoreItem> userRiskScores = buildRiskScores(recentLogs, false);
        List<AdminDashboardOverviewItem.EfficiencyScoreItem> providerEfficiencyScores = buildEfficiencyScores(
                recentLogs,
                AuditLogEntity::getProvider
        );
        List<AdminDashboardOverviewItem.EfficiencyScoreItem> modelEfficiencyScores = buildEfficiencyScores(
                recentLogs,
                entity -> entity.getModel() == null ? "unknown" : entity.getModel()
        );

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
                providerFailureSamples,
                modelFailureSamples,
                toolFailureSamples,
                blockedTrend,
                costTrend,
                guardrailTrend,
                clientRiskScores,
                userRiskScores,
                providerEfficiencyScores,
                modelEfficiencyScores,
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

    private List<AdminDashboardOverviewItem.FailureSampleItem> buildFailureSamples(
            List<AuditLogEntity> recentLogs,
            java.util.function.Function<AuditLogEntity, String> keyExtractor
    ) {
        return recentLogs.stream()
                .filter(entity -> "BLOCKED".equalsIgnoreCase(entity.getStatus()))
                .sorted(Comparator.comparing(AuditLogEntity::getCreatedAt).reversed())
                .map(entity -> new AdminDashboardOverviewItem.FailureSampleItem(
                        keyExtractor.apply(entity),
                        entity.getRequestId(),
                        firstReasonCode(entity.getRuleCodes()),
                        entity.getCreatedAt().toString()
                ))
                .limit(5)
                .toList();
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

    private List<AdminDashboardOverviewItem.RiskScoreItem> buildRiskScores(List<AuditLogEntity> recentLogs, boolean client) {
        return recentLogs.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        entity -> client ? entity.getClientId() : entity.getUserId()
                ))
                .entrySet().stream()
                .filter(entry -> entry.getKey() != null)
                .map(entry -> {
                    long blocked = entry.getValue().stream().filter(entity -> "BLOCKED".equalsIgnoreCase(entity.getStatus())).count();
                    int riskScore = (int) Math.min(100, blocked * 40 + entry.getValue().size() * 10);
                    return new AdminDashboardOverviewItem.RiskScoreItem(entry.getKey(), riskScore);
                })
                .sorted(Comparator.comparingInt(AdminDashboardOverviewItem.RiskScoreItem::score).reversed()
                        .thenComparing(AdminDashboardOverviewItem.RiskScoreItem::key))
                .limit(5)
                .toList();
    }

    private List<AdminDashboardOverviewItem.EfficiencyScoreItem> buildEfficiencyScores(
            List<AuditLogEntity> recentLogs,
            java.util.function.Function<AuditLogEntity, String> keyExtractor
    ) {
        return recentLogs.stream()
                .collect(java.util.stream.Collectors.groupingBy(keyExtractor))
                .entrySet().stream()
                .filter(entry -> entry.getKey() != null)
                .map(entry -> {
                    double totalCost = entry.getValue().stream()
                            .map(AuditLogEntity::getCostUsd)
                            .filter(Objects::nonNull)
                            .mapToDouble(Double::doubleValue)
                            .sum();
                    long totalTokens = entry.getValue().stream()
                            .map(AuditLogEntity::getTotalTokens)
                            .filter(Objects::nonNull)
                            .mapToLong(Integer::longValue)
                            .sum();
                    double efficiency = totalTokens == 0 ? 0.0d : totalTokens / Math.max(totalCost, 0.000001d);
                    return new AdminDashboardOverviewItem.EfficiencyScoreItem(entry.getKey(), efficiency);
                })
                .sorted(Comparator.comparingDouble(AdminDashboardOverviewItem.EfficiencyScoreItem::score).reversed()
                        .thenComparing(AdminDashboardOverviewItem.EfficiencyScoreItem::key))
                .limit(5)
                .toList();
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
