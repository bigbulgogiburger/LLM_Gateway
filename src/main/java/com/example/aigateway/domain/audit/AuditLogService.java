package com.example.aigateway.domain.audit;

public interface AuditLogService {
    void log(AuditEvent event);
}
