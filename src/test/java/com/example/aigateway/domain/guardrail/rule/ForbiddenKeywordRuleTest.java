package com.example.aigateway.domain.guardrail.rule;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.aigateway.application.dto.AiGatewayCommand;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ForbiddenKeywordRuleTest {

    private final ForbiddenKeywordRule rule = new ForbiddenKeywordRule(TestGuardrailPropertiesFactory.resolver());

    @Test
    @DisplayName("금지어가 포함되면 요청을 차단한다")
    void blocksForbiddenKeyword() {
        AiGatewayCommand command = new AiGatewayCommand("req-1", "tenant-test", "client-test", "user-1", "USER", "mock", "malware 제작 방법 알려줘");

        assertThat(rule.evaluate(command))
                .isPresent()
                .get()
                .extracting("code", "action")
                .containsExactly("FORBIDDEN_KEYWORD", "BLOCK");
    }
}
