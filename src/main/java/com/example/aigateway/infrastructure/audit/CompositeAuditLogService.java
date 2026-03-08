package com.example.aigateway.infrastructure.audit;

import com.example.aigateway.domain.audit.AuditEvent;
import com.example.aigateway.domain.audit.AuditLogService;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Primary
@Component
public class CompositeAuditLogService implements AuditLogService {

    private final Slf4jAuditLogService slf4jAuditLogService;
    private final JpaAuditLogWriter jpaAuditLogWriter;

    public CompositeAuditLogService(
            Slf4jAuditLogService slf4jAuditLogService,
            JpaAuditLogWriter jpaAuditLogWriter
    ) {
        this.slf4jAuditLogService = slf4jAuditLogService;
        this.jpaAuditLogWriter = jpaAuditLogWriter;
    }

    @Override
    public void log(AuditEvent event) {
        slf4jAuditLogService.write(event);
        jpaAuditLogWriter.write(event);
    }
}
