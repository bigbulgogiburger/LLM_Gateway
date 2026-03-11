package com.example.aigateway.eval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.aigateway.api.request.AiChatRequest;
import com.example.aigateway.api.response.AiChatResponse;
import com.example.aigateway.application.service.AiGatewayService;
import com.example.aigateway.common.exception.GatewayException;
import com.example.aigateway.domain.security.GatewayPrincipal;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class GatewayEvalRegressionTest {

    private static final GatewayPrincipal DEFAULT_PRINCIPAL = new GatewayPrincipal(
            "eval-client",
            "tenant-test",
            "OPERATOR",
            1000,
            50000,
            List.of("mock"),
            List.of(),
            List.of("lookup_weather", "lookup_time")
    );

    @Autowired
    private AiGatewayService aiGatewayService;

    @Autowired
    private ObjectMapper objectMapper;

    @ParameterizedTest(name = "{0}")
    @MethodSource("fixtureCases")
    @DisplayName("eval fixture는 gateway 보안/정책 기대값을 유지해야 한다")
    void runsFixtureCases(String name, EvalCase evalCase) throws Exception {
        GatewayPrincipal principal = evalCase.allowedTools() == null
                ? DEFAULT_PRINCIPAL
                : new GatewayPrincipal(
                "eval-client",
                "tenant-test",
                "OPERATOR",
                1000,
                50000,
                List.of("mock"),
                List.of(),
                evalCase.allowedTools()
        );
        AiChatRequest request = new AiChatRequest(
                "eval-user",
                null,
                "mock",
                evalCase.prompt(),
                null,
                null,
                false,
                toToolDefinitions(evalCase.tools()),
                evalCase.toolChoice() == null ? null : new AiChatRequest.ToolChoice("function", evalCase.toolChoice())
        );

        if ("THROW".equalsIgnoreCase(evalCase.expectedOutcome())) {
            assertThatThrownBy(() -> aiGatewayService.process(request, principal))
                    .isInstanceOf(GatewayException.class)
                    .satisfies(exception -> assertThat(((GatewayException) exception).code()).isEqualTo(evalCase.expectedCode()));
            return;
        }

        AiChatResponse response = aiGatewayService.process(request, principal);
        assertThat(response.status()).isEqualTo(evalCase.expectedStatus());
        if (evalCase.expectedCode() != null) {
            assertThat(response.guardrail().ruleResults()).extracting("code").contains(evalCase.expectedCode());
        }
    }

    static Stream<Arguments> fixtureCases() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        try (InputStream inputStream = GatewayEvalRegressionTest.class.getResourceAsStream("/evals/gateway-regression.json")) {
            JsonNode root = objectMapper.readTree(inputStream);
            java.util.List<Arguments> items = new java.util.ArrayList<>();
            root.path("cases").forEach(node -> items.add(Arguments.of(
                    node.path("name").asText(),
                    new EvalCase(
                            node.path("name").asText(),
                            node.path("prompt").isMissingNode() ? null : node.path("prompt").asText(null),
                            node.path("expectedStatus").isMissingNode() ? null : node.path("expectedStatus").asText(null),
                            node.path("expectedCode").isMissingNode() ? null : node.path("expectedCode").asText(null),
                            node.path("expectedOutcome").isMissingNode() ? "RESPONSE" : node.path("expectedOutcome").asText("RESPONSE"),
                            parseStringList(node.path("allowedTools")),
                            parseTools(node.path("tools")),
                            node.path("toolChoice").isMissingNode() ? null : node.path("toolChoice").asText(null)
                    )
            )));
            return items.stream();
        }
    }

    private List<AiChatRequest.ToolDefinition> toToolDefinitions(List<ToolCase> tools) {
        if (tools == null || tools.isEmpty()) {
            return null;
        }
        return tools.stream()
                .map(tool -> new AiChatRequest.ToolDefinition("function", tool.name(), tool.description(), tool.schema()))
                .toList();
    }

    private static List<ToolCase> parseTools(JsonNode node) {
        if (node == null || !node.isArray() || node.isEmpty()) {
            return List.of();
        }
        java.util.List<ToolCase> tools = new java.util.ArrayList<>();
        node.forEach(toolNode -> tools.add(new ToolCase(
                toolNode.path("name").asText(),
                toolNode.path("description").asText(),
                toolNode.path("inputSchema")
        )));
        return tools;
    }

    private static List<String> parseStringList(JsonNode node) {
        if (node == null || !node.isArray() || node.isEmpty()) {
            return null;
        }
        java.util.List<String> values = new java.util.ArrayList<>();
        node.forEach(item -> values.add(item.asText()));
        return values;
    }

    private record EvalCase(
            String name,
            String prompt,
            String expectedStatus,
            String expectedCode,
            String expectedOutcome,
            List<String> allowedTools,
            List<ToolCase> tools,
            String toolChoice
    ) {
    }

    private record ToolCase(
            String name,
            String description,
            JsonNode schema
    ) {
    }
}
