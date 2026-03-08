package com.example.aigateway.infrastructure.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({
        GuardrailProperties.class,
        OpenAiProperties.class,
        ModerationProperties.class,
        GatewaySecurityProperties.class,
        ProviderResilienceProperties.class,
        GatewayTenantPolicyProperties.class,
        GatewayRedisProperties.class,
        ToolExecutionProperties.class
})
public class GuardrailPropertiesConfig {
}
