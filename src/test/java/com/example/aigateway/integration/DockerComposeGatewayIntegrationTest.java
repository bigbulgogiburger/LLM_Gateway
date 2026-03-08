package com.example.aigateway.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.aigateway.application.service.AuditSearchService;
import com.example.aigateway.application.service.QuotaStore;
import com.example.aigateway.application.service.TenantPolicyAdminService;
import com.example.aigateway.common.exception.GatewayException;
import com.example.aigateway.infrastructure.audit.AuditSearchEntity;
import com.example.aigateway.infrastructure.audit.AuditSearchRepository;
import com.example.aigateway.infrastructure.config.GatewayRedisProperties;
import com.example.aigateway.infrastructure.config.GatewaySecurityProperties;
import com.example.aigateway.infrastructure.config.GatewayTenantPolicyProperties;
import com.example.aigateway.infrastructure.policy.TenantPolicyOverrideHistoryRepository;
import com.example.aigateway.infrastructure.policy.TenantPolicyOverrideRepository;
import com.example.aigateway.infrastructure.security.ClientRateLimiter;
import com.example.aigateway.infrastructure.security.RateLimitResult;
import com.example.aigateway.infrastructure.security.RedisClientRateLimiter;
import com.example.aigateway.application.service.RedisQuotaStore;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "RUN_DOCKER_INTEGRATION_TESTS", matches = "true")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:postgresql://localhost:5432/aigateway",
        "spring.datasource.driver-class-name=org.postgresql.Driver",
        "spring.datasource.username=aigateway",
        "spring.datasource.password=aigateway",
        "spring.jpa.hibernate.ddl-auto=update",
        "gateway.redis.enabled=true",
        "gateway.redis.namespace=ai-gateway-it"
})
class DockerComposeGatewayIntegrationTest {

    @Autowired
    private TenantPolicyAdminService tenantPolicyAdminService;

    @Autowired
    private AuditSearchService auditSearchService;

    @Autowired
    private ClientRateLimiter clientRateLimiter;

    @Autowired
    private QuotaStore quotaStore;

    @Autowired
    private TenantPolicyOverrideRepository overrideRepository;

    @Autowired
    private TenantPolicyOverrideHistoryRepository historyRepository;

    @Autowired
    private AuditSearchRepository auditSearchRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @BeforeEach
    void setUp() {
        historyRepository.deleteAll();
        overrideRepository.deleteAll();
        auditSearchRepository.deleteAll();
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushDb();
    }

    @Test
    @DisplayName("Docker compose 기반 PostgreSQL에는 tenant 정책 이력과 rollback이 저장된다")
    void persistsPolicyHistoryAndRollbackInPostgreSql() {
        GatewayTenantPolicyProperties.TenantPolicyOverride first = new GatewayTenantPolicyProperties.TenantPolicyOverride(
                new GatewayTenantPolicyProperties.InputPolicyOverride(32, List.of("malware"), null, null),
                null,
                null
        );
        GatewayTenantPolicyProperties.TenantPolicyOverride second = new GatewayTenantPolicyProperties.TenantPolicyOverride(
                new GatewayTenantPolicyProperties.InputPolicyOverride(64, List.of("malware", "credential dump"), null, null),
                null,
                null
        );

        tenantPolicyAdminService.put("tenant-it", first);
        tenantPolicyAdminService.put("tenant-it", second);
        GatewayTenantPolicyProperties.TenantPolicyOverride rolledBack = tenantPolicyAdminService.rollback("tenant-it", 1);

        assertThat(rolledBack.input().maxPromptLength()).isEqualTo(32);
        assertThat(tenantPolicyAdminService.get("tenant-it").input().maxPromptLength()).isEqualTo(32);
        assertThat(tenantPolicyAdminService.history("tenant-it"))
                .extracting("version", "action")
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(3L, "ROLLBACK"),
                        org.assertj.core.groups.Tuple.tuple(2L, "UPSERT"),
                        org.assertj.core.groups.Tuple.tuple(1L, "UPSERT")
                );
    }

    @Test
    @DisplayName("Docker compose 기반 PostgreSQL audit search는 full-text query를 통해 결과를 찾는다")
    void searchesAuditsInPostgreSql() {
        auditSearchRepository.save(new AuditSearchEntity(
                "req-it-1",
                "tenant-default",
                "local-operator",
                "mock",
                "BLOCKED",
                "credential dump security incident escalation",
                Instant.now()
        ));

        assertThat(auditSearchService.search("tenant-default", "\"credential dump\""))
                .extracting("requestId")
                .contains("req-it-1");
    }

    @Test
    @DisplayName("Docker compose 기반 Redis rate limit과 quota 경로가 실제로 동작한다")
    void enforcesRedisBackedLimitAndQuota() {
        assertThat(clientRateLimiter).isInstanceOf(RedisClientRateLimiter.class);
        assertThat(quotaStore).isInstanceOf(RedisQuotaStore.class);

        GatewaySecurityProperties.ClientProperties client = new GatewaySecurityProperties.ClientProperties(
                "redis-it-client",
                "tenant-default",
                "redis-it-api-key",
                "OPERATOR",
                2,
                1,
                10,
                List.of("mock"),
                List.of(),
                null
        );

        RateLimitResult first = clientRateLimiter.consume(client);
        RateLimitResult second = clientRateLimiter.consume(client);
        RateLimitResult third = clientRateLimiter.consume(client);

        assertThat(first.allowed()).isTrue();
        assertThat(second.allowed()).isTrue();
        assertThat(third.allowed()).isFalse();

        quotaStore.checkAndConsumeRequest("redis-it-client", 1, 10, 5);
        assertThatThrownBy(() -> quotaStore.checkAndConsumeRequest("redis-it-client", 1, 10, 5))
                .isInstanceOf(GatewayException.class)
                .hasMessageContaining("일일 요청 quota");
    }
}
