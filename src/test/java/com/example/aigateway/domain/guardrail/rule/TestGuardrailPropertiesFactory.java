package com.example.aigateway.domain.guardrail.rule;

import com.example.aigateway.domain.guardrail.service.GuardrailPolicyResolver;
import com.example.aigateway.infrastructure.config.GatewayTenantPolicyProperties;
import com.example.aigateway.infrastructure.config.GuardrailProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;

final class TestGuardrailPropertiesFactory {

    private TestGuardrailPropertiesFactory() {
    }

    static GuardrailProperties create() {
        return withMaxPromptLength(500);
    }

    static GuardrailProperties withMaxPromptLength(int maxPromptLength) {
        return new GuardrailProperties(
                new GuardrailProperties.InputPolicy(
                        maxPromptLength,
                        List.of("malware", "ransomware"),
                        List.of("admin-secret", "system prompt override"),
                        List.of(
                                new GuardrailProperties.PiiPatternPolicy("주민등록번호", "\\b\\d{6}-\\d{7}\\b", "BLOCK"),
                                new GuardrailProperties.PiiPatternPolicy("이메일", "\\b[\\w._%+-]+@[\\w.-]+\\.[A-Za-z]{2,}\\b", "BLOCK"),
                                new GuardrailProperties.PiiPatternPolicy("전화번호", "\\b01[0-9]-?\\d{3,4}-?\\d{4}\\b", "BLOCK")
                        )
                ),
                new GuardrailProperties.AiPolicy(
                        true,
                        List.of(
                                new GuardrailProperties.AiRiskPolicy("ignore previous instructions", "BLOCK", "프롬프트 인젝션 의심", 0.9d)
                        )
                ),
                new GuardrailProperties.OutputPolicy(
                        true,
                        true,
                        List.of("admin-secret", "system prompt"),
                        List.of(
                                new GuardrailProperties.PiiPatternPolicy("이메일", "\\b[\\w._%+-]+@[\\w.-]+\\.[A-Za-z]{2,}\\b", "MASK"),
                                new GuardrailProperties.PiiPatternPolicy("전화번호", "\\b01[0-9]-?\\d{3,4}-?\\d{4}\\b", "MASK")
                        ),
                        List.of(
                                new GuardrailProperties.AiRiskPolicy("기밀", "REVIEW", "기밀 정보 가능성", 0.8d)
                        )
                )
        );
    }

    static GuardrailPolicyResolver resolver() {
        return new GuardrailPolicyResolver(
                create(),
                new GatewayTenantPolicyProperties(Map.of()),
                com.example.aigateway.application.service.TenantPolicyAdminService.inMemory(new ObjectMapper())
        );
    }
}
