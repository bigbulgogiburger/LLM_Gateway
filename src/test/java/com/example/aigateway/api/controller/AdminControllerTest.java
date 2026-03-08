package com.example.aigateway.api.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.aigateway.infrastructure.audit.AuditSearchEntity;
import com.example.aigateway.infrastructure.audit.AuditSearchRepository;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class AdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuditSearchRepository auditSearchRepository;

    @Test
    @DisplayName("관리자 정책 override는 저장 후 조회할 수 있다")
    void storesTenantPolicyOverride() throws Exception {
        mockMvc.perform(post("/api/admin/tenants/tenant-enterprise/policy")
                        .header("X-API-Key", "local-admin-api-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "input": {
                                    "maxPromptLength": 42,
                                    "forbiddenKeywords": ["malware", "credential dump"]
                                  },
                                  "output": {
                                    "enabled": true,
                                    "maskPii": false,
                                    "blockedKeywords": ["root credential"]
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.input.maxPromptLength").value(42));

        mockMvc.perform(get("/api/admin/tenants/tenant-enterprise/policy/override")
                        .header("X-API-Key", "local-admin-api-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.input.maxPromptLength").value(42))
                .andExpect(jsonPath("$.output.maskPii").value(false));

        mockMvc.perform(get("/api/admin/tenants/tenant-enterprise/policy")
                        .header("X-API-Key", "local-admin-api-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.input.maxPromptLength").value(42))
                .andExpect(jsonPath("$.output.blockedKeywords[0]").value("root credential"));

        mockMvc.perform(get("/api/admin/tenants/tenant-enterprise/policy/history")
                        .header("X-API-Key", "local-admin-api-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].version").value(1))
                .andExpect(jsonPath("$[0].action").value("UPSERT"));
    }

    @Test
    @DisplayName("관리자 정책 rollback은 지정 버전을 현재 정책으로 복원하고 이력을 남긴다")
    void rollbacksTenantPolicyOverride() throws Exception {
        mockMvc.perform(post("/api/admin/tenants/tenant-rollback/policy")
                        .header("X-API-Key", "local-admin-api-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "input": {
                                    "maxPromptLength": 40
                                  }
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/admin/tenants/tenant-rollback/policy")
                        .header("X-API-Key", "local-admin-api-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "input": {
                                    "maxPromptLength": 80
                                  }
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/admin/tenants/tenant-rollback/policy/rollback/1")
                        .header("X-API-Key", "local-admin-api-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.input.maxPromptLength").value(40));

        mockMvc.perform(get("/api/admin/tenants/tenant-rollback/policy")
                        .header("X-API-Key", "local-admin-api-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.input.maxPromptLength").value(40));

        mockMvc.perform(get("/api/admin/tenants/tenant-rollback/policy/history")
                        .header("X-API-Key", "local-admin-api-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].version").value(3))
                .andExpect(jsonPath("$[0].action").value("ROLLBACK"))
                .andExpect(jsonPath("$[0].sourceVersion").value(1));
    }

    @Test
    @DisplayName("감사 로그 검색은 저장된 검색 텍스트를 기준으로 결과를 반환한다")
    void searchesAudits() throws Exception {
        auditSearchRepository.save(new AuditSearchEntity(
                "req-admin-search-1",
                "tenant-default",
                "local-operator",
                "mock",
                "BLOCKED",
                "req-admin-search-1 tenant-default local-operator mock BLOCKED credential dump incident",
                Instant.now()
        ));

        mockMvc.perform(get("/api/admin/audits/search")
                        .header("X-API-Key", "local-admin-api-key")
                        .queryParam("q", "credential dump"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].requestId").value("req-admin-search-1"))
                .andExpect(jsonPath("$[0].status").value("BLOCKED"));
    }

    @Test
    @DisplayName("관리자는 허용되지 않은 tenant 정책에 접근할 수 없다")
    void rejectsUnauthorizedTenantAccess() throws Exception {
        mockMvc.perform(get("/api/admin/tenants/tenant-denied/policy")
                        .header("X-API-Key", "local-admin-api-key"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }
}
