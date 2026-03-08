package com.example.aigateway.application.service;

import com.example.aigateway.application.dto.AuditSearchItem;
import com.example.aigateway.infrastructure.audit.AuditSearchEntity;
import com.example.aigateway.infrastructure.audit.AuditSearchRepository;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
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

    public List<AuditSearchItem> search(String tenantId, String query) {
        String normalizedQuery = query == null ? "" : query.trim();
        if (normalizedQuery.isEmpty()) {
            return List.of();
        }

        return searchEntities(tenantId, normalizedQuery).stream()
                .map(this::toItem)
                .toList();
    }

    private List<AuditSearchEntity> searchEntities(String tenantId, String query) {
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
                entity.getStatus()
        );
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
