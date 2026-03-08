package com.example.aigateway.application.dto;

import com.example.aigateway.domain.guardrail.result.AiGuardrailVerdict;

public record ModerationAssessment(
        String phase,
        String category,
        AiGuardrailVerdict verdict,
        String reason,
        double score
) {
    public static ModerationAssessment safe(String phase) {
        return new ModerationAssessment(phase, null, AiGuardrailVerdict.SAFE, "의심 패턴이 탐지되지 않았습니다.", 0.0d);
    }

    public boolean blocks() {
        return verdict == AiGuardrailVerdict.BLOCK || verdict == AiGuardrailVerdict.REVIEW;
    }

    public boolean isSafe() {
        return verdict == AiGuardrailVerdict.SAFE;
    }
}
