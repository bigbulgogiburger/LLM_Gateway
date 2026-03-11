package com.example.aigateway.infrastructure.audit;

import com.example.aigateway.domain.audit.AuditEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

@Component
public class JpaAuditLogWriter {

    private final AuditLogRepository repository;
    private final AuditSearchRepository searchRepository;
    private final ObjectMapper objectMapper;

    public JpaAuditLogWriter(AuditLogRepository repository, AuditSearchRepository searchRepository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.searchRepository = searchRepository;
        this.objectMapper = objectMapper;
    }

    public void write(AuditEvent event) {
        repository.save(new AuditLogEntity(
                event.requestId(),
                event.tenantId(),
                event.clientId(),
                event.userId(),
                event.role(),
                event.provider(),
                event.model(),
                event.status(),
                event.passed(),
                event.aiVerdict(),
                event.outputPassed(),
                event.outputModified(),
                String.join(",", event.ruleCodes()),
                event.toolCallCount(),
                String.join(",", event.toolNames()),
                event.inputTokens(),
                event.outputTokens(),
                event.totalTokens(),
                event.costUsd(),
                event.elapsedMillis(),
                event.promptSummary(),
                serializeToolExecutions(event),
                Instant.now()
        ));
        searchRepository.save(new AuditSearchEntity(
                event.requestId(),
                event.tenantId(),
                event.clientId(),
                event.provider(),
                event.model(),
                event.status(),
                event.toolCallCount(),
                String.join(",", event.toolNames()),
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
                        event.model(),
                        event.status(),
                        event.aiVerdict(),
                        String.join(" ", event.ruleCodes()),
                        String.join(" ", event.toolNames()),
                        String.valueOf(event.toolCallCount()),
                        event.promptSummary()
                )
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .collect(Collectors.joining(" "));
    }

    private String serializeToolExecutions(AuditEvent event) {
        try {
            return objectMapper.writeValueAsString(event.toolExecutions() == null ? java.util.List.of() : event.toolExecutions());
        } catch (Exception exception) {
            return "[]";
        }
    }
}
