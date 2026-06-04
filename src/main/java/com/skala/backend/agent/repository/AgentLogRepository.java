package com.skala.backend.agent.repository;

import com.skala.backend.agent.domain.AgentLog;
import com.skala.backend.agent.domain.AgentTypeCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface AgentLogRepository extends JpaRepository<AgentLog, Long> {

    List<AgentLog> findByProjectIdOrderByCreatedAtDesc(Long projectId);

    List<AgentLog> findByProjectIdAndUsageStatementIdOrderByCreatedAtDesc(Long projectId, Long usageStatementId);

    Optional<AgentLog> findTopByProjectIdAndUsageStatementIdAndAgentTypeCodeOrderByCreatedAtDesc(
            Long projectId, Long usageStatementId, AgentTypeCode agentTypeCode);

    interface AgentWarningRow {
        Long getId();
        String getAgentTypeCode();
        String getStatusCode();
        Long getUsageStatementId();
        Long getUsageStatementItemId();
        LocalDate getReportMonth();
        String getItemName();
        String getCategoryCode();
        String getDetails();
        Instant getCreatedAt();
    }

    @Query(nativeQuery = true, value = """
            SELECT
                al.id                              AS id,
                al.agent_type_code                 AS agentTypeCode,
                al.status_code                     AS statusCode,
                al.usage_statement_id              AS usageStatementId,
                al.usage_statement_item_id         AS usageStatementItemId,
                us.report_month                    AS reportMonth,
                usi.item_name                      AS itemName,
                usi.category_code                  AS categoryCode,
                al.details::text                   AS details,
                al.created_at                      AS createdAt
            FROM service.agent_logs al
            LEFT JOIN service.usage_statements us
                ON us.id = al.usage_statement_id
            LEFT JOIN service.usage_statement_items usi
                ON usi.id = al.usage_statement_item_id
            WHERE al.project_id = :projectId
              AND (:usageStatementId IS NULL OR al.usage_statement_id = :usageStatementId)
              AND (al.usage_statement_item_id IS NOT NULL OR al.status_code = 'fail')
            ORDER BY al.created_at DESC
            """)
    List<AgentWarningRow> findWarnings(
            @Param("projectId") Long projectId,
            @Param("usageStatementId") Long usageStatementId
    );
}
