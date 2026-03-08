package com.example.aigateway.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gateway.providers.openai")
public record OpenAiProperties(
        boolean enabled,
        String apiKey,
        String baseUrl,
        String model,
        String developerMessage,
        Integer maxCompletionTokens,
        Pricing pricing,
        int connectTimeoutMillis,
        int readTimeoutMillis
) {
    public record Pricing(
            double inputCostPer1kTokens,
            double outputCostPer1kTokens
    ) {
    }
}
