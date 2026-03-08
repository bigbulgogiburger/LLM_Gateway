package com.example.aigateway.application.dto;

import com.example.aigateway.domain.guardrail.result.AiGuardrailVerdict;

public record AiGuardrailAssessment(
        AiGuardrailVerdict verdict,
        String reason,
        Double score
) {
    public static AiGuardrailAssessment safe() {
        return new AiGuardrailAssessment(AiGuardrailVerdict.SAFE, "의심 패턴이 탐지되지 않았습니다.", 0.0d);
    }

    public boolean blocksRequest() {
        return verdict == AiGuardrailVerdict.BLOCK || verdict == AiGuardrailVerdict.REVIEW;
    }
}
