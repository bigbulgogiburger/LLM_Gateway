package com.example.aigateway.application.service;

import com.example.aigateway.application.dto.AuditDetailItem;
import com.example.aigateway.common.exception.GatewayErrorCodes;
import com.example.aigateway.common.exception.GatewayException;
import com.example.aigateway.infrastructure.audit.AuditLogEntity;
import com.example.aigateway.infrastructure.audit.AuditLogRepository;
import java.util.Arrays;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class AuditDetailService {

    private final AuditLogRepository auditLogRepository;

    public AuditDetailService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    public AuditDetailItem getByRequestId(String tenantId, String requestId) {
        AuditLogEntity entity = auditLogRepository.findByRequestIdAndTenantId(requestId, tenantId)
                .orElseThrow(() -> new GatewayException(
                        GatewayErrorCodes.RESOURCE_NOT_FOUND,
                        HttpStatus.NOT_FOUND,
                        "감사 로그를 찾을 수 없습니다."
                ));
        return new AuditDetailItem(
                entity.getRequestId(),
                entity.getTenantId(),
                entity.getClientId(),
                entity.getUserId(),
                entity.getRole(),
                entity.getProvider(),
                entity.getModel(),
                entity.getStatus(),
                entity.isPassed(),
                entity.getAiVerdict(),
                entity.isOutputPassed(),
                entity.isOutputModified(),
                split(entity.getRuleCodes()),
                entity.getToolCallCount(),
                split(entity.getToolNames()),
                entity.getInputTokens(),
                entity.getOutputTokens(),
                entity.getTotalTokens(),
                entity.getCostUsd(),
                entity.getElapsedMillis(),
                entity.getPromptSummary(),
                entity.getCreatedAt()
        );
    }

    private List<String> split(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(token -> !token.isEmpty())
                .toList();
    }
}
