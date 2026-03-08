package com.example.aigateway.infrastructure.audit;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuditSearchRepository extends JpaRepository<AuditSearchEntity, Long> {
    List<AuditSearchEntity> findTop50ByTenantIdOrderByIdDesc(String tenantId);

    List<AuditSearchEntity> findTop50ByTenantIdAndSearchTextContainingIgnoreCaseOrderByIdDesc(String tenantId, String searchText);

    @Query(
            value = """
                    SELECT *
                    FROM audit_search
                    WHERE tenant_id = :tenantId
                      AND to_tsvector('simple', coalesce(search_text, '')) @@ websearch_to_tsquery('simple', :query)
                    ORDER BY ts_rank_cd(
                        to_tsvector('simple', coalesce(search_text, '')),
                        websearch_to_tsquery('simple', :query)
                    ) DESC, id DESC
                    LIMIT 50
                    """,
            nativeQuery = true
    )
    List<AuditSearchEntity> searchTop50ByTenantIdFullText(@Param("tenantId") String tenantId, @Param("query") String query);
}
