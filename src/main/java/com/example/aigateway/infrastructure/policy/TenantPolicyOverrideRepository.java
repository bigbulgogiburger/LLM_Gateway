package com.example.aigateway.infrastructure.policy;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantPolicyOverrideRepository extends JpaRepository<TenantPolicyOverrideEntity, Long> {
    Optional<TenantPolicyOverrideEntity> findByTenantId(String tenantId);
}
