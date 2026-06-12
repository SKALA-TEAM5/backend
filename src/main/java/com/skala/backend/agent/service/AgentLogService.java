package com.skala.backend.agent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skala.backend.agent.domain.AgentLog;
import com.skala.backend.agent.domain.AgentTypeCode;
import com.skala.backend.agent.dto.AgentRequests;
import com.skala.backend.agent.dto.AgentResponses;
import com.skala.backend.agent.repository.AgentLogRepository;
import com.skala.backend.global.error.ApiException;
import com.skala.backend.project.service.ProjectAccessService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AgentLogService {

    private final ProjectAccessService projectAccessService;
    private final AgentLogRepository agentLogRepository;
    private final ObjectMapper objectMapper;
    private final int reportStaleSeconds;

    public AgentLogService(ProjectAccessService projectAccessService,
                           AgentLogRepository agentLogRepository,
                           ObjectMapper objectMapper,
                           @Value("${app.fastapi.stale-threshold.report-seconds:900}") int reportStaleSeconds) {
        this.projectAccessService = projectAccessService;
        this.agentLogRepository = agentLogRepository;
        this.objectMapper = objectMapper;
        this.reportStaleSeconds = reportStaleSeconds;
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

    @Transactional
    public AgentResponses.ReportDetailResponse updateReportDetails(
            Long currentUserId, Long projectId, Long usageStatementId, AgentRequests.UpdateReportRequest request) {
        // 해당 프로젝트에 배정된 admin만 보고서를 수정할 수 있다.
        projectAccessService.requireAssignedAdmin(currentUserId, projectId);

        // report agent가 실행 중(running/pending, stale 미경과)이면 결과 덮어쓰기 방지.
        if (agentLogRepository.existsActiveNonStaleLog(
                usageStatementId, AgentTypeCode.REPORT.getCode(), reportStaleSeconds)) {
            throw new ApiException(HttpStatus.CONFLICT, "현재 실행 중입니다.");
        }

        // jsonb 컬럼 저장 실패를 막기 위해 유효한 JSON인지 사전 검증.
        validateJson(request.details());

        AgentLog log = agentLogRepository
                .findTopByProjectIdAndUsageStatementIdAndAgentTypeCodeOrderByCreatedAtDesc(
                        projectId, usageStatementId, AgentTypeCode.REPORT)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "보고서 데이터가 없습니다."));
        agentLogRepository.updateDetails(log.getId(), request.details());

        // clearAutomatically로 컨텍스트를 비웠으므로 갱신된 행을 다시 읽어 응답한다.
        AgentLog updated = agentLogRepository
                .findTopByProjectIdAndUsageStatementIdAndAgentTypeCodeOrderByCreatedAtDesc(
                        projectId, usageStatementId, AgentTypeCode.REPORT)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "보고서 데이터가 없습니다."));
        return AgentResponses.ReportDetailResponse.from(updated);
    }

    private void validateJson(String json) {
        try {
            objectMapper.readTree(json);
        } catch (Exception e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "유효한 JSON 형식이 아닙니다.");
        }
    }
}
