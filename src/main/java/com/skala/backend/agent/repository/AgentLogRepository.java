package com.skala.backend.agent.repository;

import com.skala.backend.agent.domain.AgentLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AgentLogRepository extends JpaRepository<AgentLog, Long> {
    List<AgentLog> findByProjectIdAndRunIdOrderByCreatedAtAsc(Long projectId, UUID runId);
    List<AgentLog> findByProjectIdAndUsageStatementIdOrderByCreatedAtDesc(Long projectId, Long usageStatementId);
}
