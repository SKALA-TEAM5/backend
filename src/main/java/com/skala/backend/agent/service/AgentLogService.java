package com.skala.backend.agent.service;

import com.skala.backend.agent.domain.AgentLog;
import com.skala.backend.agent.domain.AgentTypeCode;
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
        List<AgentLog> logs = usageStatementId == null
                ? agentLogRepository.findByProjectIdOrderByCreatedAtDesc(projectId)
                : agentLogRepository.findByProjectIdAndUsageStatementIdOrderByCreatedAtDesc(projectId, usageStatementId);
        return logs.stream().map(AgentResponses.LogResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<AgentResponses.WarningResponse> getWarnings(Long currentUserId, Long projectId, Long usageStatementId) {
        projectAccessService.requireReadable(currentUserId, projectId);
        return agentLogRepository.findWarnings(projectId, usageStatementId)
                .stream()
                .map(AgentResponses.WarningResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public AgentResponses.LegalDetailResponse getLegalDetail(Long currentUserId, Long projectId, Long usageStatementId) {
        projectAccessService.requireReadable(currentUserId, projectId);
        AgentLog log = agentLogRepository
                .findTopByProjectIdAndUsageStatementIdAndAgentTypeCodeOrderByCreatedAtDesc(
                        projectId, usageStatementId, AgentTypeCode.LEGAL)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "법령 검증 데이터가 없습니다."));
        return AgentResponses.LegalDetailResponse.from(log);
    }

    @Transactional(readOnly = true)
    public AgentResponses.ReportDetailResponse getReportDetail(Long currentUserId, Long projectId, Long usageStatementId) {
        projectAccessService.requireReadable(currentUserId, projectId);
        AgentLog log = agentLogRepository
                .findTopByProjectIdAndUsageStatementIdAndAgentTypeCodeOrderByCreatedAtDesc(
                        projectId, usageStatementId, AgentTypeCode.REPORT)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "보고서 데이터가 없습니다."));
        return AgentResponses.ReportDetailResponse.from(log);
    }
}
