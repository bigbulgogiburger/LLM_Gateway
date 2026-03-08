package com.example.aigateway.api.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.aigateway.infrastructure.audit.AuditLogEntity;
import com.example.aigateway.infrastructure.audit.AuditLogRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class AiChatControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Test
    @DisplayName("채팅 API는 허용된 요청에 성공 응답을 반환한다")
    void returnsSuccessResponse() throws Exception {
        mockMvc.perform(post("/api/ai/chat")
                        .header("X-API-Key", "local-dev-api-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": "user-001",
                                  "role": "OPERATOR",
                                  "provider": "mock",
                                  "prompt": "이번 주 수리 현황을 요약해줘"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.tenantId").value("tenant-default"))
                .andExpect(jsonPath("$.provider").value("mock"));
    }

    @Test
    @DisplayName("채팅 API는 messages 기반 요청도 처리할 수 있다")
    void acceptsMessageBasedRequest() throws Exception {
        mockMvc.perform(post("/api/ai/chat")
                        .header("X-API-Key", "local-dev-api-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": "user-001",
                                  "provider": "mock",
                                  "messages": [
                                    {"role": "user", "content": "이번 주 수리 현황을 요약해줘"},
                                    {"role": "assistant", "content": "지난주에는 12건이었습니다."},
                                    {"role": "user", "content": "이번 주만 다시 정리해줘"}
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.data.content").value(org.hamcrest.Matchers.containsString("user: 이번 주 수리 현황을 요약해줘")));
    }

    @Test
    @DisplayName("prompt와 messages가 모두 없으면 검증 오류를 반환한다")
    void validatesPromptOrMessagesPresence() throws Exception {
        mockMvc.perform(post("/api/ai/chat")
                        .header("X-API-Key", "local-dev-api-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": "user-001",
                                  "provider": "mock"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("API 키가 없으면 인증 오류를 반환한다")
    void returnsUnauthorizedWithoutApiKey() throws Exception {
        mockMvc.perform(post("/api/ai/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": "user-001",
                                  "provider": "mock",
                                  "prompt": "이번 주 수리 현황을 요약해줘"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("actuator metrics는 admin API 키로만 접근할 수 있다")
    void protectsActuatorMetrics() throws Exception {
        mockMvc.perform(get("/actuator/metrics"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        mockMvc.perform(get("/actuator/metrics")
                        .header("X-API-Key", "local-dev-api-key"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mockMvc.perform(get("/actuator/metrics")
                        .header("X-API-Key", "local-admin-api-key"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("스트리밍 채팅 API는 SSE chunk와 done 이벤트를 반환한다")
    void streamsChatResponse() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/ai/chat/stream")
                        .header("X-API-Key", "local-dev-api-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": "user-001",
                                  "provider": "mock",
                                  "prompt": "이번 주 수리 현황을 요약해줘"
                                }
                                """))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(org.hamcrest.Matchers.containsString("event:chunk")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(org.hamcrest.Matchers.containsString("event:done")));

        AuditLogEntity audit = auditLogRepository.findTopByTenantIdOrderByIdDesc("tenant-default").orElseThrow();
        org.assertj.core.api.Assertions.assertThat(audit.getModel()).isEqualTo("mock-gateway");
        org.assertj.core.api.Assertions.assertThat(audit.getTotalTokens()).isNotNull();
        org.assertj.core.api.Assertions.assertThat(audit.getCostUsd()).isEqualTo(0.0d);
    }

    @Test
    @DisplayName("채팅 API는 tool call 메타데이터를 반환할 수 있다")
    void returnsToolCallMetadata() throws Exception {
        mockMvc.perform(post("/api/ai/chat")
                        .header("X-API-Key", "local-dev-api-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": "user-001",
                                  "provider": "mock",
                                  "prompt": "날씨를 확인해줘",
                                  "tools": [
                                    {
                                      "type": "function",
                                      "name": "lookup_weather",
                                      "description": "현재 날씨를 조회한다",
                                      "inputSchema": {
                                        "type": "object",
                                        "properties": {
                                          "city": {"type": "string"}
                                        },
                                        "required": ["city"]
                                      }
                                    }
                                  ],
                                  "toolChoice": {
                                    "type": "function",
                                    "name": "lookup_weather"
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.toolCalls[0].name").value("lookup_weather"));
    }

    @Test
    @DisplayName("스트리밍 채팅 API는 tool call 이벤트도 반환할 수 있다")
    void streamsToolCallEvent() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/ai/chat/stream")
                        .header("X-API-Key", "local-dev-api-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": "user-001",
                                  "provider": "mock",
                                  "prompt": "날씨를 확인해줘",
                                  "tools": [
                                    {
                                      "type": "function",
                                      "name": "lookup_weather",
                                      "description": "현재 날씨를 조회한다",
                                      "inputSchema": {
                                        "type": "object",
                                        "properties": {
                                          "city": {"type": "string"}
                                        },
                                        "required": ["city"]
                                      }
                                    }
                                  ],
                                  "toolChoice": {
                                    "type": "function",
                                    "name": "lookup_weather"
                                  }
                                }
                                """))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(org.hamcrest.Matchers.containsString("event:tool_call")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(org.hamcrest.Matchers.containsString("lookup_weather")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(org.hamcrest.Matchers.containsString("temperatureCelsius")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(org.hamcrest.Matchers.containsString("event:done")));

        AuditLogEntity audit = auditLogRepository.findTopByTenantIdOrderByIdDesc("tenant-default").orElseThrow();
        org.assertj.core.api.Assertions.assertThat(audit.getToolCallCount()).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(audit.getToolNames()).contains("lookup_weather");
        org.assertj.core.api.Assertions.assertThat(audit.getTotalTokens()).isNotNull();
    }
}
