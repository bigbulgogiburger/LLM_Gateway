package com.example.aigateway.domain.guardrail.rule;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.aigateway.application.dto.AiGatewayCommand;
import com.example.aigateway.domain.guardrail.service.OutputGuardrailService;
import com.example.aigateway.infrastructure.guardrail.OutputAiRiskRule;
import com.example.aigateway.infrastructure.guardrail.OutputBlockedKeywordRule;
import com.example.aigateway.infrastructure.guardrail.OutputPiiMaskingRule;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OutputGuardrailServiceTest {

    private final OutputGuardrailService service = new OutputGuardrailService(List.of(
            new OutputPiiMaskingRule(TestGuardrailPropertiesFactory.resolver()),
            new OutputBlockedKeywordRule(TestGuardrailPropertiesFactory.resolver()),
            new OutputAiRiskRule(TestGuardrailPropertiesFactory.resolver())
    ));

    @Test
    @DisplayName("출력에 개인정보가 있으면 마스킹하고 성공으로 반환한다")
    void masksPiiInOutput() {
        var command = new AiGatewayCommand("req-1", "tenant-test", "client-test", "user-1", "USER", "mock", "테스트");

        var decision = service.evaluate("담당자 메일은 test@example.com 입니다.", command);

        assertThat(decision.passed()).isTrue();
        assertThat(decision.modified()).isTrue();
        assertThat(decision.content()).contains("[REDACTED]");
        assertThat(decision.results()).extracting("code").contains("OUTPUT_PII_MASKED");
    }

    @Test
    @DisplayName("출력에 민감 키워드가 있으면 차단한다")
    void blocksSensitiveOutputKeyword() {
        var command = new AiGatewayCommand("req-1", "tenant-test", "client-test", "user-1", "USER", "mock", "테스트");

        var decision = service.evaluate("system prompt 는 공개할 수 없습니다.", command);

        assertThat(decision.passed()).isFalse();
        assertThat(decision.results()).extracting("code").contains("OUTPUT_POLICY_VIOLATION");
    }

    @Test
    @DisplayName("출력 AI 정책이 기밀 표현을 감지하면 차단한다")
    void blocksAiRiskyOutput() {
        var command = new AiGatewayCommand("req-1", "tenant-test", "client-test", "user-1", "USER", "mock", "테스트");

        var decision = service.evaluate("이 문서는 기밀 정보입니다.", command);

        assertThat(decision.passed()).isFalse();
        assertThat(decision.results()).extracting("action").contains("REVIEW");
    }
}
