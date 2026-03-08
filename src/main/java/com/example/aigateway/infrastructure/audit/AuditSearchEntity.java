package com.example.aigateway.infrastructure.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "audit_search", indexes = {
        @Index(name = "idx_audit_search_tenant", columnList = "tenantId"),
        @Index(name = "idx_audit_search_status", columnList = "status"),
        @Index(name = "idx_audit_search_provider", columnList = "provider")
})
public class AuditSearchEntity {

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
    private String provider;

    @Column(nullable = false)
    private String status;

    @Column(length = 4000)
    private String searchText;

    @Column(nullable = false)
    private Instant createdAt;

    protected AuditSearchEntity() {
    }

    public AuditSearchEntity(
            String requestId,
            String tenantId,
            String clientId,
            String provider,
            String status,
            String searchText,
            Instant createdAt
    ) {
        this.requestId = requestId;
        this.tenantId = tenantId;
        this.clientId = clientId;
        this.provider = provider;
        this.status = status;
        this.searchText = searchText;
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

    public String getProvider() {
        return provider;
    }

    public String getStatus() {
        return status;
    }

    public String getSearchText() {
        return searchText;
    }
}
