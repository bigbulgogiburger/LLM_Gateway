package com.example.aigateway.domain.guardrail.rule;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.aigateway.application.dto.AiGatewayCommand;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PiiPatternRuleTest {

    private final PiiPatternRule rule = new PiiPatternRule(TestGuardrailPropertiesFactory.resolver());

    @Test
    @DisplayName("주민등록번호 패턴이 포함되면 요청을 차단한다")
    void blocksResidentNumber() {
        AiGatewayCommand command = new AiGatewayCommand("req-1", "tenant-test", "client-test", "user-1", "USER", "mock", "900101-1234567 정보를 찾아줘");

        assertThat(rule.evaluate(command))
                .isPresent()
                .get()
                .extracting("code", "action")
                .containsExactly("PII_DETECTED", "BLOCK");
    }
}
