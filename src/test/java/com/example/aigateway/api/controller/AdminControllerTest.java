package com.example.aigateway.api.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.aigateway.infrastructure.audit.AuditLogEntity;
import com.example.aigateway.infrastructure.audit.AuditLogRepository;
import com.example.aigateway.infrastructure.audit.AuditSearchEntity;
import com.example.aigateway.infrastructure.audit.AuditSearchRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class AdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuditSearchRepository auditSearchRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @BeforeEach
    void clearRepositories() {
        auditSearchRepository.deleteAll();
        auditLogRepository.deleteAll();
    }

    @Test
    @DisplayName("관리자 정책 override는 저장 후 조회할 수 있다")
    void storesTenantPolicyOverride() throws Exception {
        mockMvc.perform(post("/api/admin/tenants/tenant-enterprise/policy")
                        .header("X-API-Key", "local-admin-api-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "input": {
                                    "maxPromptLength": 42,
                                    "forbiddenKeywords": ["malware", "credential dump"]
                                  },
                                  "output": {
                                    "enabled": true,
                                    "maskPii": false,
                                    "blockedKeywords": ["root credential"]
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.input.maxPromptLength").value(42));

        mockMvc.perform(get("/api/admin/tenants/tenant-enterprise/policy/override")
                        .header("X-API-Key", "local-admin-api-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.input.maxPromptLength").value(42))
                .andExpect(jsonPath("$.output.maskPii").value(false));

        mockMvc.perform(get("/api/admin/tenants/tenant-enterprise/policy")
                        .header("X-API-Key", "local-admin-api-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.input.maxPromptLength").value(42))
                .andExpect(jsonPath("$.output.blockedKeywords[0]").value("root credential"));

        mockMvc.perform(get("/api/admin/tenants/tenant-enterprise/policy/history")
                        .header("X-API-Key", "local-admin-api-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].version").value(1))
                .andExpect(jsonPath("$[0].action").value("UPSERT"));
    }

    @Test
    @DisplayName("관리자 정책 rollback은 지정 버전을 현재 정책으로 복원하고 이력을 남긴다")
    void rollbacksTenantPolicyOverride() throws Exception {
        mockMvc.perform(post("/api/admin/tenants/tenant-rollback/policy")
                        .header("X-API-Key", "local-admin-api-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "input": {
                                    "maxPromptLength": 40
                                  }
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/admin/tenants/tenant-rollback/policy")
                        .header("X-API-Key", "local-admin-api-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "input": {
                                    "maxPromptLength": 80
                                  }
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/admin/tenants/tenant-rollback/policy/rollback/1")
                        .header("X-API-Key", "local-admin-api-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.input.maxPromptLength").value(40));

        mockMvc.perform(get("/api/admin/tenants/tenant-rollback/policy")
                        .header("X-API-Key", "local-admin-api-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.input.maxPromptLength").value(40));

        mockMvc.perform(get("/api/admin/tenants/tenant-rollback/policy/history")
                        .header("X-API-Key", "local-admin-api-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].version").value(3))
                .andExpect(jsonPath("$[0].action").value("ROLLBACK"))
                .andExpect(jsonPath("$[0].sourceVersion").value(1));
    }

    @Test
    @DisplayName("감사 로그 검색은 저장된 검색 텍스트를 기준으로 결과를 반환한다")
    void searchesAudits() throws Exception {
        auditSearchRepository.save(new AuditSearchEntity(
                "req-admin-search-1",
                "tenant-default",
                "local-operator",
                "mock",
                "gpt-4.1-mini",
                "BLOCKED",
                2,
                "lookup_weather,lookup_time",
                "req-admin-search-1 tenant-default local-operator mock BLOCKED credential dump incident",
                Instant.now()
        ));

        mockMvc.perform(get("/api/admin/audits/search")
                        .header("X-API-Key", "local-admin-api-key")
                        .queryParam("q", "credential dump"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].requestId").value("req-admin-search-1"))
                .andExpect(jsonPath("$[0].model").value("gpt-4.1-mini"))
                .andExpect(jsonPath("$[0].status").value("BLOCKED"))
                .andExpect(jsonPath("$[0].toolCallCount").value(2))
                .andExpect(jsonPath("$[0].toolNames[0]").value("lookup_weather"));

        mockMvc.perform(get("/api/admin/audits/search")
                        .header("X-API-Key", "local-admin-api-key")
                        .queryParam("provider", "mock")
                        .queryParam("model", "gpt-4.1-mini")
                        .queryParam("tool", "lookup_time"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].requestId").value("req-admin-search-1"));
    }

    @Test
    @DisplayName("감사 로그 상세 조회는 tool usage와 cost를 반환한다")
    void getsAuditDetail() throws Exception {
        auditLogRepository.save(new AuditLogEntity(
                "req-admin-detail-1",
                "tenant-default",
                "local-operator",
                "user-001",
                "OPERATOR",
                "openai",
                "gpt-4.1-mini",
                "SUCCESS",
                true,
                "SAFE",
                true,
                false,
                "",
                2,
                "lookup_weather,lookup_time",
                120,
                80,
                200,
                0.0042d,
                150,
                "날씨와 시간을 조회해줘",
                Instant.now()
        ));

        mockMvc.perform(get("/api/admin/audits/req-admin-detail-1")
                        .header("X-API-Key", "local-admin-api-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestId").value("req-admin-detail-1"))
                .andExpect(jsonPath("$.model").value("gpt-4.1-mini"))
                .andExpect(jsonPath("$.toolCallCount").value(2))
                .andExpect(jsonPath("$.toolNames[1]").value("lookup_time"))
                .andExpect(jsonPath("$.totalTokens").value(200))
                .andExpect(jsonPath("$.costUsd").value(0.0042d));
    }

    @Test
    @DisplayName("관리자는 usage metrics 요약을 provider/model 기준으로 조회할 수 있다")
    void getsUsageMetrics() throws Exception {
        Instant now = Instant.now().truncatedTo(ChronoUnit.HOURS);
        auditLogRepository.save(new AuditLogEntity(
                "req-metrics-1",
                "tenant-default",
                "local-operator",
                "user-001",
                "OPERATOR",
                "openai",
                "gpt-4.1-mini",
                "SUCCESS",
                true,
                "SAFE",
                true,
                false,
                "",
                1,
                "lookup_weather",
                100,
                50,
                150,
                0.003d,
                120,
                "weather",
                now.minus(2, ChronoUnit.HOURS)
        ));
        auditLogRepository.save(new AuditLogEntity(
                "req-metrics-2",
                "tenant-default",
                "local-operator",
                "user-002",
                "OPERATOR",
                "openai",
                "gpt-4.1-mini",
                "BLOCKED",
                false,
                "SAFE",
                true,
                false,
                "OUTPUT_MODERATION_BLOCKED",
                1,
                "lookup_weather",
                40,
                10,
                50,
                0.001d,
                80,
                "blocked",
                now.minus(1, ChronoUnit.HOURS)
        ));
        auditLogRepository.save(new AuditLogEntity(
                "req-metrics-3",
                "tenant-default",
                "local-operator",
                "user-003",
                "OPERATOR",
                "openai",
                "gpt-4.1-mini",
                "SUCCESS",
                true,
                "SAFE",
                true,
                false,
                "",
                1,
                "lookup_time",
                60,
                20,
                80,
                0.002d,
                60,
                "time",
                now.minus(1, ChronoUnit.HOURS)
        ));
        auditLogRepository.save(new AuditLogEntity(
                "req-metrics-prev-1",
                "tenant-default",
                "local-operator",
                "user-010",
                "OPERATOR",
                "openai",
                "gpt-4.1-mini",
                "SUCCESS",
                true,
                "SAFE",
                true,
                false,
                "",
                1,
                "lookup_weather",
                70,
                30,
                100,
                0.002d,
                70,
                "previous-window",
                now.minus(30, ChronoUnit.HOURS)
        ));

        mockMvc.perform(get("/api/admin/metrics/usage")
                        .header("X-API-Key", "local-admin-api-key")
                        .queryParam("provider", "openai")
                        .queryParam("model", "gpt-4.1-mini")
                        .queryParam("tool", "lookup_weather")
                        .queryParam("sinceHours", "24"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value("tenant-default"))
                .andExpect(jsonPath("$.sinceHours").value(24))
                .andExpect(jsonPath("$.bucketUnit").value("hour"))
                .andExpect(jsonPath("$.totalRequests").value(2))
                .andExpect(jsonPath("$.successCount").value(1))
                .andExpect(jsonPath("$.blockedCount").value(1))
                .andExpect(jsonPath("$.totalTokens").value(200))
                .andExpect(jsonPath("$.totalCostUsd").value(0.004d))
                .andExpect(jsonPath("$.averageTokensPerRequest").value(100.0d))
                .andExpect(jsonPath("$.averageCostUsdPerRequest").value(0.002d))
                .andExpect(jsonPath("$.breakdown[0].provider").value("openai"))
                .andExpect(jsonPath("$.breakdown[0].model").value("gpt-4.1-mini"))
                .andExpect(jsonPath("$.providerBreakdown[0].key").value("openai"))
                .andExpect(jsonPath("$.providerBreakdown[0].requests").value(2))
                .andExpect(jsonPath("$.modelBreakdown[0].key").value("gpt-4.1-mini"))
                .andExpect(jsonPath("$.clientBreakdown[0].key").value("local-operator"))
                .andExpect(jsonPath("$.clientBreakdown[0].requests").value(2))
                .andExpect(jsonPath("$.userBreakdown[0].key").value("user-001"))
                .andExpect(jsonPath("$.userBreakdown[0].requests").value(1))
                .andExpect(jsonPath("$.toolBreakdown[0].key").value("lookup_weather"))
                .andExpect(jsonPath("$.toolBreakdown[0].requests").value(2))
                .andExpect(jsonPath("$.blockedReasonBreakdown[0].key").value("OUTPUT_MODERATION_BLOCKED"))
                .andExpect(jsonPath("$.blockedReasonBreakdown[0].requests").value(1))
                .andExpect(jsonPath("$.ruleCodeBreakdown[0].key").value("OUTPUT_MODERATION_BLOCKED"))
                .andExpect(jsonPath("$.ruleCodeBreakdown[0].blockedCount").value(1))
                .andExpect(jsonPath("$.comparison.previousTotalRequests").value(1))
                .andExpect(jsonPath("$.comparison.previousSuccessCount").value(1))
                .andExpect(jsonPath("$.comparison.previousBlockedCount").value(0))
                .andExpect(jsonPath("$.comparison.previousTotalTokens").value(100))
                .andExpect(jsonPath("$.comparison.previousTotalCostUsd").value(0.002d))
                .andExpect(jsonPath("$.comparison.requestDelta").value(1))
                .andExpect(jsonPath("$.comparison.successDelta").value(0))
                .andExpect(jsonPath("$.comparison.blockedDelta").value(1))
                .andExpect(jsonPath("$.comparison.tokenDelta").value(100))
                .andExpect(jsonPath("$.comparison.costDeltaUsd").value(0.002d))
                .andExpect(jsonPath("$.anomalyFlags[0].code").value("BLOCKED_SPIKE"))
                .andExpect(jsonPath("$.anomalyFlags[0].severity").value("warning"))
                .andExpect(jsonPath("$.timeSeries.length()").value(2))
                .andExpect(jsonPath("$.timeSeries[0].requests").value(1))
                .andExpect(jsonPath("$.timeSeries[0].totalTokens").value(150))
                .andExpect(jsonPath("$.timeSeries[1].requests").value(1))
                .andExpect(jsonPath("$.timeSeries[1].blockedCount").value(1))
                .andExpect(jsonPath("$.timeSeries[1].totalTokens").value(50));
    }

    @Test
    @DisplayName("usage metrics는 48시간을 넘기면 일 단위 bucket으로 집계한다")
    void getsDailyUsageMetricsBuckets() throws Exception {
        Instant now = Instant.now().truncatedTo(ChronoUnit.HOURS);
        auditLogRepository.save(new AuditLogEntity(
                "req-metrics-daily-1",
                "tenant-default",
                "local-operator",
                "user-101",
                "OPERATOR",
                "mock",
                "mock-gateway",
                "SUCCESS",
                true,
                "SAFE",
                true,
                false,
                "",
                1,
                "lookup_time",
                20,
                10,
                30,
                0.0d,
                30,
                "day-1",
                now.minus(26, ChronoUnit.HOURS)
        ));
        auditLogRepository.save(new AuditLogEntity(
                "req-metrics-daily-2",
                "tenant-default",
                "local-operator",
                "user-102",
                "OPERATOR",
                "mock",
                "mock-gateway",
                "SUCCESS",
                true,
                "SAFE",
                true,
                false,
                "",
                1,
                "lookup_weather",
                30,
                20,
                50,
                0.0d,
                30,
                "day-2",
                now.minus(4, ChronoUnit.HOURS)
        ));

        mockMvc.perform(get("/api/admin/metrics/usage")
                        .header("X-API-Key", "local-admin-api-key")
                        .queryParam("provider", "mock")
                        .queryParam("sinceHours", "72"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bucketUnit").value("day"))
                .andExpect(jsonPath("$.totalRequests").value(2))
                .andExpect(jsonPath("$.providerBreakdown[0].key").value("mock"))
                .andExpect(jsonPath("$.modelBreakdown[0].key").value("mock-gateway"))
                .andExpect(jsonPath("$.clientBreakdown[0].key").value("local-operator"))
                .andExpect(jsonPath("$.userBreakdown.length()").value(2))
                .andExpect(jsonPath("$.toolBreakdown.length()").value(2))
                .andExpect(jsonPath("$.blockedReasonBreakdown.length()").value(0))
                .andExpect(jsonPath("$.ruleCodeBreakdown.length()").value(0))
                .andExpect(jsonPath("$.comparison.previousTotalRequests").value(0))
                .andExpect(jsonPath("$.comparison.requestDelta").value(2))
                .andExpect(jsonPath("$.anomalyFlags.length()").value(0))
                .andExpect(jsonPath("$.timeSeries.length()").value(2))
                .andExpect(jsonPath("$.timeSeries[0].requests").value(1))
                .andExpect(jsonPath("$.timeSeries[1].requests").value(1));
    }

    @Test
    @DisplayName("관리자는 dashboard overview를 조회할 수 있다")
    void getsDashboardOverview() throws Exception {
        Instant now = Instant.now().truncatedTo(ChronoUnit.HOURS);
        auditLogRepository.save(new AuditLogEntity(
                "req-dashboard-1",
                "tenant-default",
                "client-a",
                "user-a",
                "OPERATOR",
                "openai",
                "gpt-4.1-mini",
                "SUCCESS",
                true,
                "SAFE",
                true,
                false,
                "",
                1,
                "lookup_weather",
                80,
                20,
                100,
                0.004d,
                50,
                "overview-success",
                now.minus(1, ChronoUnit.HOURS)
        ));
        auditLogRepository.save(new AuditLogEntity(
                "req-dashboard-2",
                "tenant-default",
                "client-b",
                "user-b",
                "OPERATOR",
                "openai",
                "gpt-4.1-mini",
                "BLOCKED",
                false,
                "SAFE",
                true,
                false,
                "OUTPUT_MODERATION_BLOCKED",
                0,
                "",
                30,
                10,
                40,
                0.001d,
                40,
                "overview-blocked",
                now.minus(30, ChronoUnit.MINUTES)
        ));
        auditLogRepository.save(new AuditLogEntity(
                "req-dashboard-3",
                "tenant-default",
                "client-a",
                "user-c",
                "OPERATOR",
                "mock",
                "mock-gateway",
                "SUCCESS",
                true,
                "SAFE",
                true,
                false,
                "",
                1,
                "lookup_time",
                20,
                10,
                30,
                0.0005d,
                30,
                "overview-mock",
                now.minus(2, ChronoUnit.HOURS)
        ));

        mockMvc.perform(get("/api/admin/dashboard/overview")
                        .header("X-API-Key", "local-admin-api-key")
                        .queryParam("sinceHours", "24"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value("tenant-default"))
                .andExpect(jsonPath("$.sinceHours").value(24))
                .andExpect(jsonPath("$.activeClientCount").value(2))
                .andExpect(jsonPath("$.activeUserCount").value(3))
                .andExpect(jsonPath("$.blockedRequestCount").value(1))
                .andExpect(jsonPath("$.anomalyCount").value(1))
                .andExpect(jsonPath("$.successRate").value(org.hamcrest.Matchers.closeTo(66.666d, 0.01d)))
                .andExpect(jsonPath("$.blockedRate").value(org.hamcrest.Matchers.closeTo(33.333d, 0.01d)))
                .andExpect(jsonPath("$.costPer1kTokens").value(0.032352941176470584d))
                .andExpect(jsonPath("$.healthScore").value(52))
                .andExpect(jsonPath("$.alertSeverity").value("warning"))
                .andExpect(jsonPath("$.staleTenant").value(false))
                .andExpect(jsonPath("$.lastRequestAt").exists())
                .andExpect(jsonPath("$.usage.totalRequests").value(3))
                .andExpect(jsonPath("$.usage.anomalyFlags[0].code").value("BLOCKED_SPIKE"))
                .andExpect(jsonPath("$.topProviders[0].key").value("openai"))
                .andExpect(jsonPath("$.topModels[0].key").value("gpt-4.1-mini"))
                .andExpect(jsonPath("$.topClients[0].key").value("client-a"))
                .andExpect(jsonPath("$.topUsers.length()").value(3))
                .andExpect(jsonPath("$.topBlockedReasons[0].key").value("OUTPUT_MODERATION_BLOCKED"))
                .andExpect(jsonPath("$.toolCostHotspot.key").value("lookup_weather"))
                .andExpect(jsonPath("$.providerSwitchRecommendation.code").value("REVIEW_PROVIDER_MIX"))
                .andExpect(jsonPath("$.dominantBlockedClient.key").value("client-b"))
                .andExpect(jsonPath("$.dominantBlockedUser.key").value("user-b"))
                .andExpect(jsonPath("$.peakHour.requests").value(2))
                .andExpect(jsonPath("$.providerFailureSamples[0].key").value("openai"))
                .andExpect(jsonPath("$.modelFailureSamples[0].key").value("gpt-4.1-mini"))
                .andExpect(jsonPath("$.toolFailureSamples.length()").value(0))
                .andExpect(jsonPath("$.blockedTrend[1].value").value(1))
                .andExpect(jsonPath("$.costTrend[1].value").value(5000))
                .andExpect(jsonPath("$.guardrailTrend[1].value").value(1))
                .andExpect(jsonPath("$.clientRiskScores[0].key").value("client-b"))
                .andExpect(jsonPath("$.userRiskScores[0].key").value("user-b"))
                .andExpect(jsonPath("$.providerEfficiencyScores[0].key").value("mock"))
                .andExpect(jsonPath("$.modelEfficiencyScores[0].key").value("mock-gateway"))
                .andExpect(jsonPath("$.recentBlocked[0].requestId").value("req-dashboard-2"))
                .andExpect(jsonPath("$.recentFailed[0].requestId").value("req-dashboard-2"))
                .andExpect(jsonPath("$.recentGuardrailHits[0].reasonCode").value("OUTPUT_MODERATION_BLOCKED"))
                .andExpect(jsonPath("$.recentBlocked[0].reasonCode").value("OUTPUT_MODERATION_BLOCKED"))
                .andExpect(jsonPath("$.recentBlocked[0].clientId").value("client-b"))
                .andExpect(jsonPath("$.recentCostly[0].requestId").value("req-dashboard-1"))
                .andExpect(jsonPath("$.recentCostly[1].requestId").value("req-dashboard-2"));
    }

    @Test
    @DisplayName("관리자는 provider capability 목록을 조회할 수 있다")
    void listsProviderCapabilities() throws Exception {
        mockMvc.perform(get("/api/admin/providers")
                        .header("X-API-Key", "local-admin-api-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.provider=='mock')].supportsStructuredOutputs").value(org.hamcrest.Matchers.hasItem(true)))
                .andExpect(jsonPath("$[?(@.provider=='openai')].supportsMessages").value(org.hamcrest.Matchers.hasItem(true)));
    }

    @Test
    @DisplayName("관리자는 허용되지 않은 tenant 정책에 접근할 수 없다")
    void rejectsUnauthorizedTenantAccess() throws Exception {
        mockMvc.perform(get("/api/admin/tenants/tenant-denied/policy")
                        .header("X-API-Key", "local-admin-api-key"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }
}
