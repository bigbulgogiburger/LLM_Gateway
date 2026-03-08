package com.example.aigateway.application.dto;

public record ProviderUsage(
        int inputTokens,
        int outputTokens,
        int totalTokens,
        boolean estimated,
        double costUsd
) {
    public static ProviderUsage estimated(int inputTokens, int outputTokens, double costUsd) {
        return new ProviderUsage(inputTokens, outputTokens, inputTokens + outputTokens, true, costUsd);
    }

    public ProviderUsage add(ProviderUsage other) {
        if (other == null) {
            return this;
        }
        return new ProviderUsage(
                inputTokens + other.inputTokens(),
                outputTokens + other.outputTokens(),
                totalTokens + other.totalTokens(),
                estimated && other.estimated(),
                costUsd + other.costUsd()
        );
    }
}
