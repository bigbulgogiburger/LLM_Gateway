package com.example.aigateway.infrastructure.audit;

import com.example.aigateway.domain.audit.AuditEvent;
import java.time.Instant;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

@Component
public class JpaAuditLogWriter {

    private final AuditLogRepository repository;
    private final AuditSearchRepository searchRepository;

    public JpaAuditLogWriter(AuditLogRepository repository, AuditSearchRepository searchRepository) {
        this.repository = repository;
        this.searchRepository = searchRepository;
    }

    public void write(AuditEvent event) {
        repository.save(new AuditLogEntity(
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
                String.join(",", event.ruleCodes()),
                event.elapsedMillis(),
                event.promptSummary(),
                Instant.now()
        ));
        searchRepository.save(new AuditSearchEntity(
                event.requestId(),
                event.tenantId(),
                event.clientId(),
                event.provider(),
                event.status(),
                buildSearchText(event),
                Instant.now()
        ));
    }

    private String buildSearchText(AuditEvent event) {
        return Stream.of(
                        event.requestId(),
                        event.tenantId(),
                        event.clientId(),
                        event.provider(),
                        event.status(),
                        event.aiVerdict(),
                        String.join(" ", event.ruleCodes()),
                        event.promptSummary()
                )
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .collect(Collectors.joining(" "));
    }
}
