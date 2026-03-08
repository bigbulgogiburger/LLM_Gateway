package com.example.aigateway.infrastructure.audit;

import com.example.aigateway.domain.audit.AuditEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class Slf4jAuditLogService {

    private static final Logger log = LoggerFactory.getLogger(Slf4jAuditLogService.class);

    public void write(AuditEvent event) {
        log.info(
                "requestId={} tenantId={} clientId={} userId={} role={} provider={} status={} passed={} aiVerdict={} outputPassed={} outputModified={} ruleCodes={} promptSummary={} elapsedMs={}",
                event.requestId(),
                event.tenantId(),
                event.clientId(),
                event.userId(),
                event.role(),
                event.provider(),
                event.status(),
                event.passed(),
                event.aiVerdict(),
                event.outputPassed(),
                event.outputModified(),
                event.ruleCodes(),
                event.promptSummary(),
                event.elapsedMillis()
        );
    }
}
