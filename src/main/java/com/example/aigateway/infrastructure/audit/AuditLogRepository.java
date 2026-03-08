package com.example.aigateway.infrastructure.audit;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLogEntity, Long> {
    Optional<AuditLogEntity> findByRequestIdAndTenantId(String requestId, String tenantId);

    Optional<AuditLogEntity> findTopByTenantIdOrderByIdDesc(String tenantId);

    List<AuditLogEntity> findTop500ByTenantIdAndCreatedAtAfterOrderByCreatedAtDesc(String tenantId, Instant createdAt);
}
