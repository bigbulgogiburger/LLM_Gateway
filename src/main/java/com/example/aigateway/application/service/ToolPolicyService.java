package com.example.aigateway.application.service;

import com.example.aigateway.api.request.AiChatRequest;
import com.example.aigateway.common.exception.GatewayErrorCodes;
import com.example.aigateway.common.exception.GatewayException;
import com.example.aigateway.domain.security.GatewayPrincipal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class ToolPolicyService {

    private final ToolExecutionService toolExecutionService;
    private final ToolSchemaValidator toolSchemaValidator;

    public ToolPolicyService(ToolExecutionService toolExecutionService, ToolSchemaValidator toolSchemaValidator) {
        this.toolExecutionService = toolExecutionService;
        this.toolSchemaValidator = toolSchemaValidator;
    }

    public void validateRequestedTools(GatewayPrincipal principal, List<AiChatRequest.ToolDefinition> tools, AiChatRequest.ToolChoice toolChoice) {
        if (tools == null || tools.isEmpty()) {
            return;
        }
        Set<String> requestedNames = new HashSet<>();
        for (AiChatRequest.ToolDefinition tool : tools) {
            if (!"function".equalsIgnoreCase(tool.type())) {
                throw new GatewayException(
                        GatewayErrorCodes.VALIDATION_ERROR,
                        HttpStatus.BAD_REQUEST,
                        "현재는 function 타입 tool만 지원합니다."
                );
            }
            requestedNames.add(tool.name().toLowerCase());
            if (!principal.allowsTool(tool.name())) {
                throw new GatewayException(
                        GatewayErrorCodes.FORBIDDEN,
                        HttpStatus.FORBIDDEN,
                        "현재 API Key로는 요청한 tool을 사용할 수 없습니다: " + tool.name()
                );
            }
            if (!toolExecutionService.isRegistered(tool.name())) {
                throw new GatewayException(
                        GatewayErrorCodes.VALIDATION_ERROR,
                        HttpStatus.BAD_REQUEST,
                        "등록되지 않은 tool 입니다: " + tool.name()
                );
            }
            toolSchemaValidator.validateRequestSchema(tool.name(), toolExecutionService.inputSchema(tool.name()), tool.inputSchema());
        }
        if (toolChoice != null && "function".equalsIgnoreCase(toolChoice.type())
                && (toolChoice.name() == null || !requestedNames.contains(toolChoice.name().toLowerCase()))) {
            throw new GatewayException(
                    GatewayErrorCodes.VALIDATION_ERROR,
                    HttpStatus.BAD_REQUEST,
                    "toolChoice는 요청한 tools 목록에 포함되어야 합니다."
            );
        }
    }
}
