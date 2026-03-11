package com.example.aigateway.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.aigateway.api.request.AiChatRequest;
import com.example.aigateway.common.exception.GatewayException;
import com.example.aigateway.domain.security.GatewayPrincipal;
import com.example.aigateway.domain.tool.ExecutableTool;
import com.example.aigateway.infrastructure.config.ToolExecutionProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ToolPolicyServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("요청 tool schema가 등록된 schema와 다르면 거부된다")
    void rejectsSchemaMismatch() throws Exception {
        ToolExecutionService toolExecutionService = new ToolExecutionService(
                objectMapper,
                List.of(new SchemaAwareTool(objectMapper)),
                new ToolExecutionProperties(2000, 1, 80),
                new ToolSchemaValidator()
        );
        ToolPolicyService toolPolicyService = new ToolPolicyService(toolExecutionService, new ToolSchemaValidator());
        GatewayPrincipal principal = new GatewayPrincipal(
                "test-client",
                "tenant-test",
                "OPERATOR",
                1000,
                50000,
                List.of("mock"),
                List.of(),
                List.of("schema_tool")
        );

        assertThatThrownBy(() -> toolPolicyService.validateRequestedTools(
                principal,
                List.of(new AiChatRequest.ToolDefinition(
                        "function",
                        "schema_tool",
                        "schema tool",
                        objectMapper.readTree("""
                                {
                                  "type": "object",
                                  "properties": {
                                    "city": {"type": "integer"}
                                  },
                                  "required": ["city"]
                                }
                                """)
                )),
                new AiChatRequest.ToolChoice("function", "schema_tool")
        ))
                .isInstanceOf(GatewayException.class)
                .satisfies(exception -> {
                    GatewayException gatewayException = (GatewayException) exception;
                    assertThat(gatewayException.code()).isEqualTo("VALIDATION_ERROR");
                });
    }

    @Test
    @DisplayName("tool 자체 scope 정책에 맞지 않으면 요청 단계에서 거부된다")
    void rejectsToolOutsideScope() throws Exception {
        ToolExecutionService toolExecutionService = new ToolExecutionService(
                objectMapper,
                List.of(new AdminScopedTool(objectMapper)),
                new ToolExecutionProperties(2000, 1, 80),
                new ToolSchemaValidator()
        );
        ToolPolicyService toolPolicyService = new ToolPolicyService(toolExecutionService, new ToolSchemaValidator());
        GatewayPrincipal principal = new GatewayPrincipal(
                "test-client",
                "tenant-test",
                "OPERATOR",
                1000,
                50000,
                List.of("mock"),
                List.of(),
                List.of("admin_scoped_tool")
        );

        assertThatThrownBy(() -> toolPolicyService.validateRequestedTools(
                principal,
                List.of(new AiChatRequest.ToolDefinition(
                        "function",
                        "admin_scoped_tool",
                        "admin scoped tool",
                        objectMapper.readTree("""
                                {
                                  "type": "object",
                                  "properties": {},
                                  "required": []
                                }
                                """)
                )),
                new AiChatRequest.ToolChoice("function", "admin_scoped_tool")
        ))
                .isInstanceOf(GatewayException.class)
                .satisfies(exception -> assertThat(((GatewayException) exception).code()).isEqualTo("FORBIDDEN"));
    }

    private record SchemaAwareTool(ObjectMapper objectMapper) implements ExecutableTool {
        @Override
        public String name() {
            return "schema_tool";
        }

        @Override
        public JsonNode inputSchema() {
            ObjectNode schema = objectMapper.createObjectNode();
            schema.put("type", "object");
            ObjectNode properties = objectMapper.createObjectNode();
            properties.set("city", objectMapper.createObjectNode().put("type", "string"));
            schema.set("properties", properties);
            schema.putArray("required").add("city");
            return schema;
        }

        @Override
        public JsonNode execute(JsonNode arguments) {
            return arguments;
        }
    }

    private record AdminScopedTool(ObjectMapper objectMapper) implements ExecutableTool {
        @Override
        public String name() {
            return "admin_scoped_tool";
        }

        @Override
        public JsonNode execute(JsonNode arguments) {
            return objectMapper.createObjectNode().put("ok", true);
        }

        @Override
        public List<String> allowedRoles() {
            return List.of("ADMIN");
        }
    }
}
