package com.example.aigateway.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.aigateway.application.dto.ProviderResult;
import com.example.aigateway.application.dto.ProviderUsage;
import com.example.aigateway.domain.security.GatewayPrincipal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class QuotaEnforcementServiceTest {

    @Test
    @DisplayName("actual usage가 있으면 추정 prompt 토큰과의 차이만큼 quota를 보정한다")
    void adjustsQuotaWithProviderUsage() {
        RecordingQuotaStore quotaStore = new RecordingQuotaStore();
        QuotaEnforcementService service = new QuotaEnforcementService(new TokenEstimator(), quotaStore);
        GatewayPrincipal principal = new GatewayPrincipal(
                "client-1",
                "tenant-1",
                "OPERATOR",
                1000,
                50000,
                List.of("mock"),
                List.of(),
                List.of("lookup_weather")
        );

        service.recordProviderUsage(
                principal,
                "12345678",
                new ProviderResult("ok", List.of(), "resp-1", "gpt-4.1-mini", new ProviderUsage(1, 9, 10, false, 0.01d))
        );

        assertThat(quotaStore.adjustedTokens).isEqualTo(8);
    }

    private static final class RecordingQuotaStore implements QuotaStore {
        private int adjustedTokens;

        @Override
        public void checkAndConsumeRequest(String clientId, int requestQuota, int tokenQuota, int promptTokens) {
        }

        @Override
        public void recordResponseTokens(String clientId, int responseTokens) {
        }

        @Override
        public void adjustTokens(String clientId, int deltaTokens) {
            adjustedTokens += deltaTokens;
        }
    }
}
