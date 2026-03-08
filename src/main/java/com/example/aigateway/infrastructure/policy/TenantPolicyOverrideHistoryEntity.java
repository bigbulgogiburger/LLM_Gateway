package com.example.aigateway.infrastructure.policy;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(
        name = "tenant_policy_override_history",
        indexes = @Index(name = "idx_policy_history_tenant_version", columnList = "tenantId, version", unique = true)
)
public class TenantPolicyOverrideHistoryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String tenantId;

    @Column(nullable = false)
    private Long version;

    @Column(nullable = false, length = 16000)
    private String overrideJson;

    @Column(nullable = false, length = 32)
    private String action;

    @Column
    private Long sourceVersion;

    @Column(nullable = false)
    private Instant createdAt;

    protected TenantPolicyOverrideHistoryEntity() {
    }

    public TenantPolicyOverrideHistoryEntity(
            String tenantId,
            Long version,
            String overrideJson,
            String action,
            Long sourceVersion,
            Instant createdAt
    ) {
        this.tenantId = tenantId;
        this.version = version;
        this.overrideJson = overrideJson;
        this.action = action;
        this.sourceVersion = sourceVersion;
        this.createdAt = createdAt;
    }

    public String getTenantId() {
        return tenantId;
    }

    public Long getVersion() {
        return version;
    }

    public String getOverrideJson() {
        return overrideJson;
    }

    public String getAction() {
        return action;
    }

    public Long getSourceVersion() {
        return sourceVersion;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
