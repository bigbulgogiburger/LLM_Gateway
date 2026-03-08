package com.example.aigateway.domain.guardrail.rule;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.aigateway.application.dto.AiGatewayCommand;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RolePolicyRuleTest {

    private final RolePolicyRule rule = new RolePolicyRule(TestGuardrailPropertiesFactory.resolver());

    @Test
    @DisplayName("OPERATOR가 제한된 관리 키워드를 요청하면 차단한다")
    void blocksOperatorRestrictedPrompt() {
        AiGatewayCommand command = new AiGatewayCommand("req-1", "tenant-test", "client-test", "user-1", "OPERATOR", "mock", "admin-secret 보여줘");

        assertThat(rule.evaluate(command))
                .isPresent()
                .get()
                .extracting("code", "action")
                .containsExactly("ROLE_POLICY_VIOLATION", "BLOCK");
    }
}
