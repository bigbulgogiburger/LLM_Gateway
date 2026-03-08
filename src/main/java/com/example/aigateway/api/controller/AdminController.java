package com.example.aigateway.api.controller;

import com.example.aigateway.application.dto.AuditSearchItem;
import com.example.aigateway.application.dto.TenantPolicyOverrideHistoryItem;
import com.example.aigateway.application.service.AuditSearchService;
import com.example.aigateway.common.exception.GatewayErrorCodes;
import com.example.aigateway.common.exception.GatewayException;
import com.example.aigateway.domain.guardrail.result.ResolvedGuardrailPolicy;
import com.example.aigateway.domain.guardrail.service.GuardrailPolicyResolver;
import com.example.aigateway.application.service.TenantPolicyAdminService;
import com.example.aigateway.domain.security.GatewayPrincipal;
import com.example.aigateway.infrastructure.config.GatewayTenantPolicyProperties;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final TenantPolicyAdminService tenantPolicyAdminService;
    private final AuditSearchService auditSearchService;
    private final GuardrailPolicyResolver guardrailPolicyResolver;

    public AdminController(
            TenantPolicyAdminService tenantPolicyAdminService,
            AuditSearchService auditSearchService,
            GuardrailPolicyResolver guardrailPolicyResolver
    ) {
        this.tenantPolicyAdminService = tenantPolicyAdminService;
        this.auditSearchService = auditSearchService;
        this.guardrailPolicyResolver = guardrailPolicyResolver;
    }

    @GetMapping("/tenants/{tenantId}/policy/override")
    public GatewayTenantPolicyProperties.TenantPolicyOverride getOverridePolicy(
            @PathVariable String tenantId,
            @AuthenticationPrincipal GatewayPrincipal principal
    ) {
        ensureTenantAccess(principal, tenantId);
        return tenantPolicyAdminService.get(tenantId);
    }

    @GetMapping("/tenants/{tenantId}/policy")
    public ResolvedGuardrailPolicy getPolicy(
            @PathVariable String tenantId,
            @AuthenticationPrincipal GatewayPrincipal principal
    ) {
        ensureTenantAccess(principal, tenantId);
        return guardrailPolicyResolver.resolve(tenantId);
    }

    @PostMapping("/tenants/{tenantId}/policy")
    public GatewayTenantPolicyProperties.TenantPolicyOverride putPolicy(
            @PathVariable String tenantId,
            @AuthenticationPrincipal GatewayPrincipal principal,
            @RequestBody GatewayTenantPolicyProperties.TenantPolicyOverride override
    ) {
        ensureTenantAccess(principal, tenantId);
        return tenantPolicyAdminService.put(tenantId, override);
    }

    @GetMapping("/tenants/{tenantId}/policy/history")
    public List<TenantPolicyOverrideHistoryItem> getPolicyHistory(
            @PathVariable String tenantId,
            @AuthenticationPrincipal GatewayPrincipal principal
    ) {
        ensureTenantAccess(principal, tenantId);
        return tenantPolicyAdminService.history(tenantId);
    }

    @PostMapping("/tenants/{tenantId}/policy/rollback/{version}")
    public GatewayTenantPolicyProperties.TenantPolicyOverride rollbackPolicy(
            @PathVariable String tenantId,
            @AuthenticationPrincipal GatewayPrincipal principal,
            @PathVariable long version
    ) {
        ensureTenantAccess(principal, tenantId);
        return tenantPolicyAdminService.rollback(tenantId, version);
    }

    @GetMapping("/audits/search")
    public List<AuditSearchItem> searchAudits(
            @RequestParam String q,
            @AuthenticationPrincipal GatewayPrincipal principal
    ) {
        return auditSearchService.search(principal.tenantId(), q);
    }

    private void ensureTenantAccess(GatewayPrincipal principal, String tenantId) {
        if (principal == null || !principal.canManageTenant(tenantId)) {
            throw new GatewayException(
                    GatewayErrorCodes.FORBIDDEN,
                    HttpStatus.FORBIDDEN,
                    "해당 tenant에 접근할 권한이 없습니다."
            );
        }
    }
}
