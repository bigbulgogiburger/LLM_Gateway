package com.example.aigateway.infrastructure.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gateway.moderation")
public record ModerationProperties(
        PhasePolicy input,
        PhasePolicy output
) {
    public record PhasePolicy(
            boolean enabled,
            List<ModerationRule> rules
    ) {
    }

    public record ModerationRule(
            String category,
            String pattern,
            String verdict,
            String reason,
            double score
    ) {
    }
}
