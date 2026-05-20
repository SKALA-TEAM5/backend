package com.skala.backend.agent.service;

import com.skala.backend.agent.dto.AgentResponses;
import com.skala.backend.agent.repository.AgentLogRepository;
import com.skala.backend.global.error.ApiException;
import com.skala.backend.project.service.ProjectAccessService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class AgentLogService {

    private final ProjectAccessService projectAccessService;
    private final AgentLogRepository agentLogRepository;

    public AgentLogService(ProjectAccessService projectAccessService, AgentLogRepository agentLogRepository) {
        this.projectAccessService = projectAccessService;
        this.agentLogRepository = agentLogRepository;
    }

    @Transactional(readOnly = true)
    public List<AgentResponses.LogResponse> getLogs(Long currentUserId, Long projectId, UUID runId, Long usageStatementId) {
        projectAccessService.requireReadable(currentUserId, projectId);
        if (runId != null) {
            return agentLogRepository.findByProjectIdAndRunIdOrderByCreatedAtAsc(projectId, runId)
                    .stream().map(AgentResponses.LogResponse::from).toList();
        }
        if (usageStatementId != null) {
            return agentLogRepository.findByProjectIdAndUsageStatementIdOrderByCreatedAtDesc(projectId, usageStatementId)
                    .stream().map(AgentResponses.LogResponse::from).toList();
        }
        throw new ApiException(HttpStatus.BAD_REQUEST, "runId 또는 usageStatementId 중 하나는 필수입니다.");
    }

    @Transactional(readOnly = true)
    public List<AgentResponses.WarningResponse> getWarnings(Long currentUserId, Long projectId, Long usageStatementId) {
        projectAccessService.requireReadable(currentUserId, projectId);
        return agentLogRepository.findWarnings(projectId, usageStatementId)
                .stream()
                .map(AgentResponses.WarningResponse::from)
                .toList();
    }
}
