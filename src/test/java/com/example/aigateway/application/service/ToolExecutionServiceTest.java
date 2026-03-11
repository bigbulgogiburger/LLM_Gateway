package com.example.aigateway.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.aigateway.application.dto.ProviderToolCall;
import com.example.aigateway.application.dto.ToolExecutionResult;
import com.example.aigateway.common.exception.GatewayException;
import com.example.aigateway.domain.security.GatewayPrincipal;
import com.example.aigateway.domain.tool.ExecutableTool;
import com.example.aigateway.infrastructure.config.ToolExecutionProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ToolExecutionServiceTest {

    private static final GatewayPrincipal PRINCIPAL = new GatewayPrincipal(
            "test-client",
            "tenant-test",
            "OPERATOR",
            1000,
            50000,
            List.of("mock"),
            List.of(),
            List.of("fast_tool", "slow_tool", "flaky_tool", "large_output_tool")
    );

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("tool 실행 결과에는 인자 요약과 실행 시간이 포함된다")
    void includesExecutionMetadata() {
        ToolExecutionService service = new ToolExecutionService(
                objectMapper,
                List.of(new FastTool(objectMapper)),
                new ToolExecutionProperties(2000, 1, 80),
                new ToolSchemaValidator()
        );

        List<ToolExecutionResult> results = service.execute(PRINCIPAL, List.of(
                new ProviderToolCall("tool-1", "call-1", "fast_tool", "{\"city\":\"Seoul\"}")
        ));

        assertThat(results).hasSize(1);
        assertThat(results.get(0).toolName()).isEqualTo("fast_tool");
        assertThat(results.get(0).argumentsSummary()).contains("Seoul");
        assertThat(results.get(0).output()).contains("\"ok\":true");
        assertThat(results.get(0).durationMillis()).isGreaterThanOrEqualTo(0);
        assertThat(results.get(0).attempts()).isEqualTo(1);
        assertThat(results.get(0).truncated()).isFalse();
    }

    @Test
    @DisplayName("tool 실행이 timeout을 넘기면 실패한다")
    void failsOnTimeout() {
        ToolExecutionService service = new ToolExecutionService(
                objectMapper,
                List.of(new SlowTool()),
                new ToolExecutionProperties(50, 1, 80),
                new ToolSchemaValidator()
        );

        assertThatThrownBy(() -> service.execute(PRINCIPAL, List.of(
                new ProviderToolCall("tool-2", "call-2", "slow_tool", "{}")
        )))
                .isInstanceOf(GatewayException.class)
                .satisfies(exception -> {
                    GatewayException gatewayException = (GatewayException) exception;
                    assertThat(gatewayException.code()).isEqualTo("PROVIDER_EXECUTION_FAILED");
                    assertThat(gatewayException.getMessage()).contains("시간이 초과");
                });
    }

    @Test
    @DisplayName("tool 인자가 등록된 schema와 다르면 거부된다")
    void rejectsInvalidArguments() {
        ToolExecutionService service = new ToolExecutionService(
                objectMapper,
                List.of(new FastTool(objectMapper)),
                new ToolExecutionProperties(2000, 1, 80),
                new ToolSchemaValidator()
        );

        assertThatThrownBy(() -> service.execute(PRINCIPAL, List.of(
                new ProviderToolCall("tool-3", "call-3", "fast_tool", "{\"city\":123}")
        )))
                .isInstanceOf(GatewayException.class)
                .satisfies(exception -> {
                    GatewayException gatewayException = (GatewayException) exception;
                    assertThat(gatewayException.code()).isEqualTo("VALIDATION_ERROR");
                });
    }

    @Test
    @DisplayName("retry 가능한 tool은 일시 실패 후 재시도에 성공한다")
    void retriesTransientFailures() {
        ToolExecutionService service = new ToolExecutionService(
                objectMapper,
                List.of(new FlakyTool(objectMapper)),
                new ToolExecutionProperties(2000, 1, 80),
                new ToolSchemaValidator()
        );

        List<ToolExecutionResult> results = service.execute(PRINCIPAL, List.of(
                new ProviderToolCall("tool-4", "call-4", "flaky_tool", "{\"city\":\"Seoul\"}")
        ));

        assertThat(results).hasSize(1);
        assertThat(results.get(0).attempts()).isEqualTo(2);
        assertThat(results.get(0).output()).contains("\"recovered\":true");
    }

    @Test
    @DisplayName("tool output이 너무 크면 안전한 preview envelope로 잘린다")
    void truncatesLargeOutput() {
        ToolExecutionService service = new ToolExecutionService(
                objectMapper,
                List.of(new LargeOutputTool(objectMapper)),
                new ToolExecutionProperties(2000, 1, 60),
                new ToolSchemaValidator()
        );

        List<ToolExecutionResult> results = service.execute(PRINCIPAL, List.of(
                new ProviderToolCall("tool-5", "call-5", "large_output_tool", "{}")
        ));

        assertThat(results.get(0).truncated()).isTrue();
        assertThat(results.get(0).output()).contains("\"truncated\":true");
        assertThat(results.get(0).output()).contains("\"contentPreview\"");
    }

    @Test
    @DisplayName("tool 자체 정책에 허용되지 않은 role이면 거부된다")
    void rejectsScopedToolForDisallowedRole() {
        ToolExecutionService service = new ToolExecutionService(
                objectMapper,
                List.of(new AdminOnlyTool(objectMapper)),
                new ToolExecutionProperties(2000, 1, 80),
                new ToolSchemaValidator()
        );
        GatewayPrincipal operator = new GatewayPrincipal(
                "test-client",
                "tenant-test",
                "OPERATOR",
                1000,
                50000,
                List.of("mock"),
                List.of(),
                List.of("admin_only_tool")
        );

        assertThatThrownBy(() -> service.execute(operator, List.of(
                new ProviderToolCall("tool-6", "call-6", "admin_only_tool", "{}")
        )))
                .isInstanceOf(GatewayException.class)
                .satisfies(exception -> assertThat(((GatewayException) exception).code()).isEqualTo("FORBIDDEN"));
    }

    private record FastTool(ObjectMapper objectMapper) implements ExecutableTool {
        @Override
        public String name() {
            return "fast_tool";
        }

        @Override
        public JsonNode execute(JsonNode arguments) {
            ObjectNode result = objectMapper.createObjectNode();
            result.put("ok", true);
            result.set("echo", arguments);
            return result;
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
    }

    private static final class SlowTool implements ExecutableTool {
        @Override
        public String name() {
            return "slow_tool";
        }

        @Override
        public JsonNode execute(JsonNode arguments) {
            try {
                Thread.sleep(150);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
            return new ObjectMapper().createObjectNode().put("ok", true);
        }

        @Override
        public Duration timeout() {
            return Duration.ofMillis(50);
        }
    }

    private static final class FlakyTool implements ExecutableTool {
        private final ObjectMapper objectMapper;
        private int attempts;

        private FlakyTool(ObjectMapper objectMapper) {
            this.objectMapper = objectMapper;
        }

        @Override
        public String name() {
            return "flaky_tool";
        }

        @Override
        public JsonNode execute(JsonNode arguments) {
            attempts++;
            if (attempts == 1) {
                throw new IllegalStateException("temporary failure");
            }
            return objectMapper.createObjectNode().put("recovered", true);
        }

        @Override
        public boolean isRetryable(Throwable throwable) {
            return true;
        }
    }

    private record LargeOutputTool(ObjectMapper objectMapper) implements ExecutableTool {
        @Override
        public String name() {
            return "large_output_tool";
        }

        @Override
        public JsonNode execute(JsonNode arguments) {
            return objectMapper.createObjectNode().put("payload", "x".repeat(160));
        }
    }

    private record AdminOnlyTool(ObjectMapper objectMapper) implements ExecutableTool {
        @Override
        public String name() {
            return "admin_only_tool";
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
