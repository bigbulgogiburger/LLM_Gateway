package com.example.aigateway.infrastructure.policy;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantPolicyOverrideHistoryRepository extends JpaRepository<TenantPolicyOverrideHistoryEntity, Long> {
    Optional<TenantPolicyOverrideHistoryEntity> findTopByTenantIdOrderByVersionDesc(String tenantId);

    Optional<TenantPolicyOverrideHistoryEntity> findByTenantIdAndVersion(String tenantId, Long version);

    List<TenantPolicyOverrideHistoryEntity> findByTenantIdOrderByVersionDesc(String tenantId);
}
