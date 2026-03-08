package com.example.aigateway.domain.guardrail.service;

import com.example.aigateway.application.dto.AiGatewayCommand;
import com.example.aigateway.application.dto.AiGuardrailAssessment;

public interface AiGuardrailService {
    AiGuardrailAssessment assess(AiGatewayCommand command);
}
