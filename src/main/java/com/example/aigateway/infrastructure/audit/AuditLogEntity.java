package com.example.aigateway.infrastructure.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "audit_log")
public class AuditLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String requestId;

    @Column(nullable = false)
    private String tenantId;

    @Column(nullable = false)
    private String clientId;

    @Column(nullable = false)
    private String userId;

    @Column(nullable = false)
    private String role;

    @Column(nullable = false)
    private String provider;

    private String model;

    @Column(nullable = false)
    private String status;

    @Column(nullable = false)
    private boolean passed;

    private String aiVerdict;

    @Column(nullable = false)
    private boolean outputPassed;

    @Column(nullable = false)
    private boolean outputModified;

    @Column(length = 1000)
    private String ruleCodes;

    @Column(nullable = false)
    private int toolCallCount;

    @Column(length = 1000)
    private String toolNames;

    private Integer inputTokens;

    private Integer outputTokens;

    private Integer totalTokens;

    private Double costUsd;

    @Column(nullable = false)
    private long elapsedMillis;

    @Column(length = 500)
    private String promptSummary;

    @Column(nullable = false)
    private Instant createdAt;

    protected AuditLogEntity() {
    }

    public AuditLogEntity(
            String requestId,
            String tenantId,
            String clientId,
            String userId,
            String role,
            String provider,
            String model,
            String status,
            boolean passed,
            String aiVerdict,
            boolean outputPassed,
            boolean outputModified,
            String ruleCodes,
            int toolCallCount,
            String toolNames,
            Integer inputTokens,
            Integer outputTokens,
            Integer totalTokens,
            Double costUsd,
            long elapsedMillis,
            String promptSummary,
            Instant createdAt
    ) {
        this.requestId = requestId;
        this.tenantId = tenantId;
        this.clientId = clientId;
        this.userId = userId;
        this.role = role;
        this.provider = provider;
        this.model = model;
        this.status = status;
        this.passed = passed;
        this.aiVerdict = aiVerdict;
        this.outputPassed = outputPassed;
        this.outputModified = outputModified;
        this.ruleCodes = ruleCodes;
        this.toolCallCount = toolCallCount;
        this.toolNames = toolNames;
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.totalTokens = totalTokens;
        this.costUsd = costUsd;
        this.elapsedMillis = elapsedMillis;
        this.promptSummary = promptSummary;
        this.createdAt = createdAt;
    }

    public String getRequestId() {
        return requestId;
    }

    public String getTenantId() {
        return tenantId;
    }

    public String getClientId() {
        return clientId;
    }

    public String getUserId() {
        return userId;
    }

    public String getRole() {
        return role;
    }

    public String getProvider() {
        return provider;
    }

    public String getModel() {
        return model;
    }

    public String getStatus() {
        return status;
    }

    public boolean isPassed() {
        return passed;
    }

    public String getAiVerdict() {
        return aiVerdict;
    }

    public boolean isOutputPassed() {
        return outputPassed;
    }

    public boolean isOutputModified() {
        return outputModified;
    }

    public String getRuleCodes() {
        return ruleCodes;
    }

    public int getToolCallCount() {
        return toolCallCount;
    }

    public String getToolNames() {
        return toolNames;
    }

    public Integer getInputTokens() {
        return inputTokens;
    }

    public Integer getOutputTokens() {
        return outputTokens;
    }

    public Integer getTotalTokens() {
        return totalTokens;
    }

    public Double getCostUsd() {
        return costUsd;
    }

    public long getElapsedMillis() {
        return elapsedMillis;
    }

    public String getPromptSummary() {
        return promptSummary;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
