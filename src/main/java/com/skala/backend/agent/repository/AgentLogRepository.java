package com.skala.backend.agent.repository;

import com.skala.backend.agent.domain.AgentLog;
import com.skala.backend.agent.domain.AgentLogStatus;
import com.skala.backend.agent.domain.AgentTypeCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AgentLogRepository extends JpaRepository<AgentLog, Long> {

    List<AgentLog> findByProjectIdOrderByCreatedAtDesc(Long projectId);

    List<AgentLog> findByProjectIdAndUsageStatementIdOrderByCreatedAtDesc(Long projectId, Long usageStatementId);

    Optional<AgentLog> findTopByProjectIdAndUsageStatementIdAndAgentTypeCodeOrderByCreatedAtDesc(
            Long projectId, Long usageStatementId, AgentTypeCode agentTypeCode);

    boolean existsByUsageStatementIdAndAgentTypeCodeAndUsageStatementItemIdIsNull(
            Long usageStatementId, AgentTypeCode agentTypeCode);

    boolean existsByUsageStatementIdAndAgentTypeCodeAndStatusInAndUsageStatementItemIdIsNull(
            Long usageStatementId, AgentTypeCode agentTypeCode, Collection<AgentLogStatus> statuses);

    @Query(nativeQuery = true, value = """
            SELECT EXISTS(
                SELECT 1 FROM service.agent_logs
                WHERE usage_statement_id = :statementId
                  AND agent_type_code    = :agentTypeCode
                  AND status_code        = 'success'
                  AND result_code        = :resultCode
                  AND usage_statement_item_id IS NULL
            )
            """)
    boolean existsStatementLogWithExactResultCode(
            @Param("statementId")   Long   usageStatementId,
            @Param("agentTypeCode") String agentTypeCode,
            @Param("resultCode")    String resultCode
    );

    @Query(nativeQuery = true, value = """
            SELECT EXISTS(
                SELECT 1 FROM service.agent_logs
                WHERE usage_statement_id = :statementId
                  AND agent_type_code    = :agentTypeCode
                  AND status_code        = 'success'
                  AND result_code        IN ('success', 'hil')
                  AND usage_statement_item_id IS NULL
            )
            """)
    boolean existsStatementLogWithSuccessOrHil(
            @Param("statementId")   Long   usageStatementId,
            @Param("agentTypeCode") String agentTypeCode
    );

    @Query(nativeQuery = true, value = """
            SELECT EXISTS(
                SELECT 1 FROM service.agent_logs
                WHERE usage_statement_id = :statementId
                  AND agent_type_code    = :agentTypeCode
                  AND status_code        = 'success'
                  AND usage_statement_item_id IS NULL
            )
            """)
    boolean existsStatementLogWithAnyResult(
            @Param("statementId")   Long   usageStatementId,
            @Param("agentTypeCode") String agentTypeCode
    );

    interface AgentTodoRow {
        String getAgentTypeCode();
        String getStatusCode();
        String getResultCode();
        String getReason();
        String getDetails();
    }

    @Query(nativeQuery = true, value = """
            SELECT
                al.agent_type_code  AS agentTypeCode,
                al.status_code      AS statusCode,
                al.result_code      AS resultCode,
                al.reason           AS reason,
                al.details::text    AS details
            FROM service.agent_logs al
            WHERE al.usage_statement_id = :statementId
              AND al.agent_type_code IN ('safety-doc', 'link', 'vision', 'legal')
              AND al.usage_statement_item_id IS NULL
              AND (al.status_code = 'fail' OR al.result_code IN ('hil', 'fail'))
            """)
    List<AgentTodoRow> findTodoLogs(@Param("statementId") Long statementId);

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

    @Query(nativeQuery = true, value = """
            SELECT EXISTS(
                SELECT 1 FROM service.agent_logs
                WHERE usage_statement_id = :statementId
                  AND agent_type_code    = :agentTypeCode
                  AND status_code        IN ('running', 'pending')
                  AND usage_statement_item_id IS NULL
                  AND COALESCE(updated_at, created_at) >= NOW() - (:thresholdSeconds * INTERVAL '1 second')
            )
            """)
    boolean existsActiveNonStaleLog(
            @Param("statementId")       Long   usageStatementId,
            @Param("agentTypeCode")     String agentTypeCode,
            @Param("thresholdSeconds")  int    thresholdSeconds
    );

    @Transactional
    @Modifying
    @Query(nativeQuery = true, value = """
            INSERT INTO service.agent_logs
                (project_id, usage_statement_id, agent_type_code, status_code, created_at, updated_at)
            VALUES
                (:projectId, :statementId, :agentTypeCode, 'fail', NOW(), NOW())
            ON CONFLICT (usage_statement_id, agent_type_code)
            WHERE usage_statement_item_id IS NULL
            DO UPDATE SET status_code = 'fail'
            """)
    void upsertStatementLogFail(
            @Param("projectId")     Long   projectId,
            @Param("statementId")   Long   usageStatementId,
            @Param("agentTypeCode") String agentTypeCode
    );
}
