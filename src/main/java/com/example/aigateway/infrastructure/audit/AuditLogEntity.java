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
            String status,
            boolean passed,
            String aiVerdict,
            boolean outputPassed,
            boolean outputModified,
            String ruleCodes,
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
        this.status = status;
        this.passed = passed;
        this.aiVerdict = aiVerdict;
        this.outputPassed = outputPassed;
        this.outputModified = outputModified;
        this.ruleCodes = ruleCodes;
        this.elapsedMillis = elapsedMillis;
        this.promptSummary = promptSummary;
        this.createdAt = createdAt;
    }
}
