package com.skala.backend.agent.repository;

import com.skala.backend.agent.domain.AgentUsageRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public interface AgentUsageRecordRepository extends JpaRepository<AgentUsageRecord, Long> {

    interface ByUserRow {
        Long getUserId();
        String getUserName();
        Long getInputTokens();
        Long getOutputTokens();
        BigDecimal getCostUsd();
        Long getCallCount();
    }

    interface ByProjectRow {
        Long getProjectId();
        String getProjectName();
        Long getInputTokens();
        Long getOutputTokens();
        BigDecimal getCostUsd();
        Long getCallCount();
    }

    interface ByAgentRow {
        String getAgentTypeCode();
        Long getInputTokens();
        Long getOutputTokens();
        BigDecimal getCostUsd();
        Long getCallCount();
    }

    interface ByMonthRow {
        String getMonth();
        Long getInputTokens();
        Long getOutputTokens();
        BigDecimal getCostUsd();
        Long getCallCount();
    }

    interface ByDateRow {
        String getDate();
        Long getInputTokens();
        Long getOutputTokens();
        BigDecimal getCostUsd();
        Long getCallCount();
    }

    @Query(nativeQuery = true, value = """
            SELECT
                u.id                                    AS userId,
                u.real_name                             AS userName,
                COALESCE(SUM(r.input_tokens), 0)        AS inputTokens,
                COALESCE(SUM(r.output_tokens), 0)       AS outputTokens,
                COALESCE(SUM(r.cost_usd), 0)            AS costUsd,
                COUNT(*)                                AS callCount
            FROM service.agent_usage_records r
            JOIN service.users u ON u.id = r.user_id
            WHERE (:userId IS NULL OR r.user_id = :userId)
              AND (:projectId IS NULL OR r.project_id = :projectId)
              AND (:agentTypeCode IS NULL OR r.agent_type_code = :agentTypeCode)
              AND (CAST(:from AS TIMESTAMPTZ) IS NULL OR r.created_at >= :from)
              AND (CAST(:to AS TIMESTAMPTZ) IS NULL OR r.created_at < :to)
            GROUP BY u.id, u.real_name
            ORDER BY COALESCE(SUM(r.cost_usd), 0) DESC
            """)
    List<ByUserRow> groupByUser(
            @Param("userId") Long userId,
            @Param("projectId") Long projectId,
            @Param("agentTypeCode") String agentTypeCode,
            @Param("from") Instant from,
            @Param("to") Instant to
    );

    @Query(nativeQuery = true, value = """
            SELECT
                p.id                                    AS projectId,
                p.project_name                          AS projectName,
                COALESCE(SUM(r.input_tokens), 0)        AS inputTokens,
                COALESCE(SUM(r.output_tokens), 0)       AS outputTokens,
                COALESCE(SUM(r.cost_usd), 0)            AS costUsd,
                COUNT(*)                                AS callCount
            FROM service.agent_usage_records r
            JOIN service.projects p ON p.id = r.project_id
            WHERE (:userId IS NULL OR r.user_id = :userId)
              AND (:projectId IS NULL OR r.project_id = :projectId)
              AND (:agentTypeCode IS NULL OR r.agent_type_code = :agentTypeCode)
              AND (CAST(:from AS TIMESTAMPTZ) IS NULL OR r.created_at >= :from)
              AND (CAST(:to AS TIMESTAMPTZ) IS NULL OR r.created_at < :to)
            GROUP BY p.id, p.project_name
            ORDER BY COALESCE(SUM(r.cost_usd), 0) DESC
            """)
    List<ByProjectRow> groupByProject(
            @Param("userId") Long userId,
            @Param("projectId") Long projectId,
            @Param("agentTypeCode") String agentTypeCode,
            @Param("from") Instant from,
            @Param("to") Instant to
    );

    @Query(nativeQuery = true, value = """
            SELECT
                r.agent_type_code                       AS agentTypeCode,
                COALESCE(SUM(r.input_tokens), 0)        AS inputTokens,
                COALESCE(SUM(r.output_tokens), 0)       AS outputTokens,
                COALESCE(SUM(r.cost_usd), 0)            AS costUsd,
                COUNT(*)                                AS callCount
            FROM service.agent_usage_records r
            WHERE (:userId IS NULL OR r.user_id = :userId)
              AND (:projectId IS NULL OR r.project_id = :projectId)
              AND (:agentTypeCode IS NULL OR r.agent_type_code = :agentTypeCode)
              AND (CAST(:from AS TIMESTAMPTZ) IS NULL OR r.created_at >= :from)
              AND (CAST(:to AS TIMESTAMPTZ) IS NULL OR r.created_at < :to)
            GROUP BY r.agent_type_code
            ORDER BY COALESCE(SUM(r.cost_usd), 0) DESC
            """)
    List<ByAgentRow> groupByAgent(
            @Param("userId") Long userId,
            @Param("projectId") Long projectId,
            @Param("agentTypeCode") String agentTypeCode,
            @Param("from") Instant from,
            @Param("to") Instant to
    );

    @Query(nativeQuery = true, value = """
            SELECT
                TO_CHAR(r.created_at AT TIME ZONE 'UTC', 'YYYY-MM') AS month,
                COALESCE(SUM(r.input_tokens), 0)        AS inputTokens,
                COALESCE(SUM(r.output_tokens), 0)       AS outputTokens,
                COALESCE(SUM(r.cost_usd), 0)            AS costUsd,
                COUNT(*)                                AS callCount
            FROM service.agent_usage_records r
            WHERE (:userId IS NULL OR r.user_id = :userId)
              AND (:projectId IS NULL OR r.project_id = :projectId)
              AND (:agentTypeCode IS NULL OR r.agent_type_code = :agentTypeCode)
              AND (CAST(:from AS TIMESTAMPTZ) IS NULL OR r.created_at >= :from)
              AND (CAST(:to AS TIMESTAMPTZ) IS NULL OR r.created_at < :to)
            GROUP BY TO_CHAR(r.created_at AT TIME ZONE 'UTC', 'YYYY-MM')
            ORDER BY month ASC
            """)
    List<ByMonthRow> groupByMonth(
            @Param("userId") Long userId,
            @Param("projectId") Long projectId,
            @Param("agentTypeCode") String agentTypeCode,
            @Param("from") Instant from,
            @Param("to") Instant to
    );

    @Query(nativeQuery = true, value = """
            SELECT
                TO_CHAR(r.created_at AT TIME ZONE 'UTC', 'YYYY-MM-DD') AS date,
                COALESCE(SUM(r.input_tokens), 0)        AS inputTokens,
                COALESCE(SUM(r.output_tokens), 0)       AS outputTokens,
                COALESCE(SUM(r.cost_usd), 0)            AS costUsd,
                COUNT(*)                                AS callCount
            FROM service.agent_usage_records r
            WHERE (:userId IS NULL OR r.user_id = :userId)
              AND (:projectId IS NULL OR r.project_id = :projectId)
              AND (:agentTypeCode IS NULL OR r.agent_type_code = :agentTypeCode)
              AND (CAST(:from AS TIMESTAMPTZ) IS NULL OR r.created_at >= :from)
              AND (CAST(:to AS TIMESTAMPTZ) IS NULL OR r.created_at < :to)
            GROUP BY TO_CHAR(r.created_at AT TIME ZONE 'UTC', 'YYYY-MM-DD')
            ORDER BY date ASC
            """)
    List<ByDateRow> groupByDate(
            @Param("userId") Long userId,
            @Param("projectId") Long projectId,
            @Param("agentTypeCode") String agentTypeCode,
            @Param("from") Instant from,
            @Param("to") Instant to
    );

    /**
     * 사용내역서 삭제 시 비용 기록은 보존하되 statement 참조만 끊는다(SET NULL).
     * 프로젝트 단위 비용 집계는 그대로 유지된다.
     */
    @Transactional
    @Modifying
    @Query("UPDATE AgentUsageRecord r SET r.usageStatementId = null WHERE r.usageStatementId = :usageStatementId")
    void clearUsageStatementId(@Param("usageStatementId") Long usageStatementId);
}
