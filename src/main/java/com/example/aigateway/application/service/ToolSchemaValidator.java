package com.example.aigateway.application.service;

import com.example.aigateway.common.exception.GatewayErrorCodes;
import com.example.aigateway.common.exception.GatewayException;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class ToolSchemaValidator {

    public void validateRequestSchema(String toolName, JsonNode expectedSchema, JsonNode providedSchema) {
        if (providedSchema == null || providedSchema.isMissingNode() || providedSchema.isNull()) {
            throw new GatewayException(
                    GatewayErrorCodes.VALIDATION_ERROR,
                    HttpStatus.BAD_REQUEST,
                    "tool inputSchema가 필요합니다: " + toolName
            );
        }
        if (!expectedSchema.equals(providedSchema)) {
            throw new GatewayException(
                    GatewayErrorCodes.VALIDATION_ERROR,
                    HttpStatus.BAD_REQUEST,
                    "tool inputSchema가 등록된 정의와 다릅니다: " + toolName
            );
        }
    }

    public void validateArguments(String toolName, JsonNode schema, JsonNode arguments) {
        if (schema == null || schema.isMissingNode() || schema.isNull()) {
            return;
        }
        if (!"object".equals(schema.path("type").asText())) {
            return;
        }
        if (arguments == null || !arguments.isObject()) {
            throw invalid(toolName, "object 타입 인자가 필요합니다.");
        }

        JsonNode properties = schema.path("properties");
        Set<String> allowedFields = new HashSet<>();
        if (properties.isObject()) {
            Iterator<String> fieldNames = properties.fieldNames();
            while (fieldNames.hasNext()) {
                allowedFields.add(fieldNames.next());
            }
        }

        arguments.fieldNames().forEachRemaining(field -> {
            if (!allowedFields.contains(field)) {
                throw invalid(toolName, "허용되지 않은 인자입니다: " + field);
            }
        });

        JsonNode required = schema.path("required");
        if (required.isArray()) {
            for (JsonNode field : required) {
                String fieldName = field.asText();
                if (!arguments.has(fieldName) || arguments.get(fieldName).isNull()) {
                    throw invalid(toolName, "필수 인자가 누락되었습니다: " + fieldName);
                }
            }
        }

        if (properties.isObject()) {
            properties.fields().forEachRemaining(entry -> validateType(toolName, entry.getKey(), entry.getValue(), arguments.get(entry.getKey())));
        }
    }

    private void validateType(String toolName, String fieldName, JsonNode propertySchema, JsonNode value) {
        if (value == null || value.isNull()) {
            return;
        }
        String type = propertySchema.path("type").asText();
        boolean valid = switch (type) {
            case "string" -> value.isTextual();
            case "integer" -> value.isIntegralNumber();
            case "number" -> value.isNumber();
            case "boolean" -> value.isBoolean();
            case "object" -> value.isObject();
            case "array" -> value.isArray();
            default -> true;
        };
        if (!valid) {
            throw invalid(toolName, "인자 타입이 올바르지 않습니다: " + fieldName);
        }
    }

    private GatewayException invalid(String toolName, String message) {
        return new GatewayException(
                GatewayErrorCodes.VALIDATION_ERROR,
                HttpStatus.BAD_REQUEST,
                "tool schema 검증 실패 [" + toolName + "]: " + message
        );
    }
}
