package com.example.aigateway.infrastructure.audit;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLogEntity, Long> {
    Optional<AuditLogEntity> findByRequestIdAndTenantId(String requestId, String tenantId);
}
