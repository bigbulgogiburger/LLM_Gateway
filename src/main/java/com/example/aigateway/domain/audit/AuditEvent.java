package com.example.aigateway.domain.audit;

import java.util.List;

public record AuditEvent(
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
        List<String> ruleCodes,
        long elapsedMillis,
        String promptSummary
) {
}
