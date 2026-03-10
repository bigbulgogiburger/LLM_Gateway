package com.example.aigateway.application.service;

import com.example.aigateway.application.dto.AuditSearchItem;
import com.example.aigateway.infrastructure.audit.AuditSearchEntity;
import com.example.aigateway.infrastructure.audit.AuditSearchRepository;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Arrays;
import javax.sql.DataSource;
import org.springframework.stereotype.Service;

@Service
public class AuditSearchService {

    private final AuditSearchRepository repository;
    private final DataSource dataSource;

    public AuditSearchService(AuditSearchRepository repository, DataSource dataSource) {
        this.repository = repository;
        this.dataSource = dataSource;
    }

    public List<AuditSearchItem> search(String tenantId, String query, String provider, String model, String tool, String status, String clientId) {
        String normalizedQuery = query == null ? "" : query.trim();
        String normalizedProvider = provider == null ? "" : provider.trim();
        String normalizedModel = model == null ? "" : model.trim();
        String normalizedTool = tool == null ? "" : tool.trim();
        String normalizedStatus = status == null ? "" : status.trim();
        String normalizedClientId = clientId == null ? "" : clientId.trim();
        if (normalizedQuery.isEmpty() && normalizedProvider.isEmpty() && normalizedModel.isEmpty() && normalizedTool.isEmpty()
                && normalizedStatus.isEmpty() && normalizedClientId.isEmpty()) {
            return List.of();
        }

        return searchEntities(tenantId, normalizedQuery).stream()
                .filter(entity -> normalizedProvider.isEmpty() || normalizedProvider.equalsIgnoreCase(entity.getProvider()))
                .filter(entity -> normalizedModel.isEmpty() || normalizedModel.equalsIgnoreCase(entity.getModel()))
                .filter(entity -> normalizedStatus.isEmpty() || normalizedStatus.equalsIgnoreCase(entity.getStatus()))
                .filter(entity -> normalizedClientId.isEmpty() || normalizedClientId.equalsIgnoreCase(entity.getClientId()))
                .filter(entity -> normalizedTool.isEmpty() || splitToolNames(entity.getToolNames()).stream()
                        .anyMatch(toolName -> toolName.equalsIgnoreCase(normalizedTool)))
                .map(this::toItem)
                .toList();
    }

    private List<AuditSearchEntity> searchEntities(String tenantId, String query) {
        if (query == null || query.isBlank()) {
            return repository.findTop50ByTenantIdOrderByIdDesc(tenantId);
        }
        if (isPostgreSql()) {
            return repository.searchTop50ByTenantIdFullText(tenantId, query);
        }
        return repository.findTop50ByTenantIdAndSearchTextContainingIgnoreCaseOrderByIdDesc(tenantId, query);
    }

    private AuditSearchItem toItem(AuditSearchEntity entity) {
        return new AuditSearchItem(
                entity.getRequestId(),
                entity.getTenantId(),
                entity.getClientId(),
                entity.getProvider(),
                entity.getModel(),
                entity.getStatus(),
                entity.getToolCallCount(),
                splitToolNames(entity.getToolNames())
        );
    }

    private List<String> splitToolNames(String toolNames) {
        if (toolNames == null || toolNames.isBlank()) {
            return List.of();
        }
        return Arrays.stream(toolNames.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toList();
    }

    private boolean isPostgreSql() {
        try (Connection connection = dataSource.getConnection()) {
            String productName = connection.getMetaData().getDatabaseProductName();
            return productName != null && productName.toLowerCase().contains("postgresql");
        } catch (SQLException exception) {
            return false;
        }
    }
}
