package com.example.aigateway.domain.guardrail.rule;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.aigateway.application.service.TenantPolicyAdminService;
import com.example.aigateway.application.dto.AiGatewayCommand;
import com.example.aigateway.domain.guardrail.service.GuardrailPolicyResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PromptLengthRuleTest {

    private final PromptLengthRule rule = new PromptLengthRule(new GuardrailPolicyResolver(
            TestGuardrailPropertiesFactory.withMaxPromptLength(10),
            new com.example.aigateway.infrastructure.config.GatewayTenantPolicyProperties(java.util.Map.of()),
            TenantPolicyAdminService.inMemory(new ObjectMapper())
    ));

    @Test
    @DisplayName("프롬프트 길이 제한을 초과하면 요청을 차단한다")
    void blocksLongPrompt() {
        AiGatewayCommand command = new AiGatewayCommand("req-1", "tenant-test", "client-test", "user-1", "USER", "mock", "12345678901");

        assertThat(rule.evaluate(command))
                .isPresent()
                .get()
                .extracting("code", "action")
                .containsExactly("PROMPT_TOO_LONG", "BLOCK");
    }
}
