package com.skala.backend.agent.service;

import com.skala.backend.agent.dto.AgentResponses;
import com.skala.backend.agent.repository.AgentLogRepository;
import com.skala.backend.global.error.ApiException;
import com.skala.backend.project.service.ProjectAccessService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AgentLogService {

    private final ProjectAccessService projectAccessService;
    private final AgentLogRepository agentLogRepository;

    public AgentLogService(ProjectAccessService projectAccessService, AgentLogRepository agentLogRepository) {
        this.projectAccessService = projectAccessService;
        this.agentLogRepository = agentLogRepository;
    }

    @Transactional(readOnly = true)
    public List<AgentResponses.LogResponse> getLogs(Long currentUserId, Long projectId, Long usageStatementId) {
        projectAccessService.requireReadable(currentUserId, projectId);
        if (usageStatementId == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "usageStatementId는 필수입니다.");
        }
        return agentLogRepository.findByProjectIdAndUsageStatementIdOrderByCreatedAtDesc(projectId, usageStatementId)
                .stream().map(AgentResponses.LogResponse::from).toList();
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
