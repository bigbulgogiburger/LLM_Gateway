package com.example.aigateway.api.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class AiChatControllerTest {

    @Autowired
    private MockMvc mockMvc;

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
}
