package com.example.aigateway.infrastructure.policy;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "tenant_policy_override")
public class TenantPolicyOverrideEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String tenantId;

    @Column(nullable = false, length = 16000)
    private String overrideJson;

    @Column(nullable = false)
    private Instant updatedAt;

    protected TenantPolicyOverrideEntity() {
    }

    public TenantPolicyOverrideEntity(String tenantId, String overrideJson, Instant updatedAt) {
        this.tenantId = tenantId;
        this.overrideJson = overrideJson;
        this.updatedAt = updatedAt;
    }

    public TenantPolicyOverrideEntity(Long id, String tenantId, String overrideJson, Instant updatedAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.overrideJson = overrideJson;
        this.updatedAt = updatedAt;
    }

    public Long getId() {
        return id;
    }

    public String getTenantId() {
        return tenantId;
    }

    public String getOverrideJson() {
        return overrideJson;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
