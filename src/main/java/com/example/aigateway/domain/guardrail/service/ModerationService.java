package com.example.aigateway.domain.guardrail.service;

import com.example.aigateway.application.dto.AiGatewayCommand;
import com.example.aigateway.application.dto.ModerationAssessment;

public interface ModerationService {
    ModerationAssessment assessInput(AiGatewayCommand command);

    ModerationAssessment assessOutput(String content, AiGatewayCommand command);
}
