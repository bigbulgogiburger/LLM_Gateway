package com.example.aigateway.infrastructure.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({
        GuardrailProperties.class,
        OpenAiProperties.class,
        GatewaySecurityProperties.class,
        ProviderResilienceProperties.class,
        GatewayTenantPolicyProperties.class,
        GatewayRedisProperties.class
})
public class GuardrailPropertiesConfig {
}
