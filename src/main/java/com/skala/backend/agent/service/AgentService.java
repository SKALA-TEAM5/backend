package com.skala.backend.agent.service;

import com.skala.backend.agent.client.FastApiAgentClient;
import com.skala.backend.agent.domain.AgentLogStatus;
import com.skala.backend.agent.domain.AgentTypeCode;
import com.skala.backend.agent.dto.AgentRequests;
import com.skala.backend.agent.dto.AgentResponses;
import com.skala.backend.agent.repository.AgentLogRepository;
import com.skala.backend.global.error.ApiException;
import com.skala.backend.project.service.ProjectAccessService;
import com.skala.backend.usage.domain.UsageStatementStatus;
import com.skala.backend.usage.repository.UsageStatementRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AgentService {

	private final FastApiAgentClient fastApiAgentClient;
	private final ProjectAccessService projectAccessService;
	private final AgentLogRepository agentLogRepository;
	private final UsageStatementRepository usageStatementRepository;

	public AgentService(FastApiAgentClient fastApiAgentClient, ProjectAccessService projectAccessService,
			AgentLogRepository agentLogRepository, UsageStatementRepository usageStatementRepository) {
		this.fastApiAgentClient = fastApiAgentClient;
		this.projectAccessService = projectAccessService;
		this.agentLogRepository = agentLogRepository;
		this.usageStatementRepository = usageStatementRepository;
	}

	public AgentResponses.ParseResult parse(Long currentUserId, Long projectId, AgentRequests.ParseRequest request) {
		projectAccessService.requireReadable(currentUserId, projectId);
		return fastApiAgentClient.parseUsageStatement(projectId, request.fileId());
	}

	public List<AgentResponses.AgentRunResult> validate(Long currentUserId, Long projectId, AgentRequests.ValidateRequest request) {
		projectAccessService.requireReadable(currentUserId, projectId);
		return fastApiAgentClient.runValidation(projectId, request.usageStatementId(), currentUserId);
	}

	public AgentResponses.AgentRunResult legal(Long currentUserId, Long projectId, AgentRequests.LegalRequest request) {
		projectAccessService.requireReadable(currentUserId, projectId);
		String status = usageStatementRepository.findById(request.usageStatementId())
				.map(s -> s.getStatusCode())
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "사용내역서를 찾을 수 없습니다."));
		if (!UsageStatementStatus.UPLOAD_COMPLETED.getCode().equals(status)) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "제출된 사용내역서에서만 법령 검증을 실행할 수 있습니다.");
		}
		if (!agentLogRepository.existsByUsageStatementIdAndAgentTypeCodeAndUsageStatementItemIdIsNull(
				request.usageStatementId(), AgentTypeCode.SAFETY_DOC)) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "validate를 먼저 실행해야 합니다.");
		}
		if (agentLogRepository.existsByUsageStatementIdAndAgentTypeCodeAndStatusInAndUsageStatementItemIdIsNull(
				request.usageStatementId(), AgentTypeCode.LEGAL,
				List.of(AgentLogStatus.RUNNING, AgentLogStatus.PENDING))) {
			throw new ApiException(HttpStatus.CONFLICT, "현재 실행 중입니다.");
		}
		return fastApiAgentClient.runLegal(projectId, request.usageStatementId(), currentUserId);
	}

	public AgentResponses.AgentRunResult report(Long currentUserId, Long projectId, AgentRequests.ReportRequest request) {
		projectAccessService.requireReadable(currentUserId, projectId);
		if (!agentLogRepository.existsByUsageStatementIdAndAgentTypeCodeAndUsageStatementItemIdIsNull(
				request.usageStatementId(), AgentTypeCode.LEGAL)) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "legal을 먼저 실행해야 합니다.");
		}
		if (agentLogRepository.existsByUsageStatementIdAndAgentTypeCodeAndStatusInAndUsageStatementItemIdIsNull(
				request.usageStatementId(), AgentTypeCode.REPORT,
				List.of(AgentLogStatus.RUNNING, AgentLogStatus.PENDING))) {
			throw new ApiException(HttpStatus.CONFLICT, "현재 실행 중입니다.");
		}
		return fastApiAgentClient.runReport(projectId, request.usageStatementId(), currentUserId);
	}

	public AgentResponses.ButtonStatesResponse getButtonStates(Long currentUserId, Long projectId, Long usageStatementId) {
		projectAccessService.requireReadable(currentUserId, projectId);

		List<AgentLogStatus> running = List.of(AgentLogStatus.RUNNING, AgentLogStatus.PENDING);

		boolean safetyDocRunning = agentLogRepository
				.existsByUsageStatementIdAndAgentTypeCodeAndStatusInAndUsageStatementItemIdIsNull(
						usageStatementId, AgentTypeCode.SAFETY_DOC, running);
		boolean legalRunning = agentLogRepository
				.existsByUsageStatementIdAndAgentTypeCodeAndStatusInAndUsageStatementItemIdIsNull(
						usageStatementId, AgentTypeCode.LEGAL, running);
		boolean reportRunning = agentLogRepository
				.existsByUsageStatementIdAndAgentTypeCodeAndStatusInAndUsageStatementItemIdIsNull(
						usageStatementId, AgentTypeCode.REPORT, running);

		boolean hasSafetyDocLog = agentLogRepository
				.existsByUsageStatementIdAndAgentTypeCodeAndUsageStatementItemIdIsNull(
						usageStatementId, AgentTypeCode.SAFETY_DOC);
		boolean hasLegalLog = agentLogRepository
				.existsByUsageStatementIdAndAgentTypeCodeAndUsageStatementItemIdIsNull(
						usageStatementId, AgentTypeCode.LEGAL);

		AgentResponses.ButtonState validateState = safetyDocRunning
				? new AgentResponses.ButtonState(false, "현재 실행 중입니다.")
				: new AgentResponses.ButtonState(true, null);

		AgentResponses.ButtonState legalState;
		if (safetyDocRunning || legalRunning) {
			legalState = new AgentResponses.ButtonState(false, "현재 실행 중입니다.");
		} else if (!hasSafetyDocLog) {
			legalState = new AgentResponses.ButtonState(false, "validate를 먼저 실행해야 합니다.");
		} else {
			legalState = new AgentResponses.ButtonState(true, null);
		}

		AgentResponses.ButtonState reportState;
		if (legalRunning || reportRunning) {
			reportState = new AgentResponses.ButtonState(false, "현재 실행 중입니다.");
		} else if (!hasSafetyDocLog) {
			reportState = new AgentResponses.ButtonState(false, "validate를 먼저 실행해야 합니다.");
		} else if (!hasLegalLog) {
			reportState = new AgentResponses.ButtonState(false, "legal을 먼저 실행해야 합니다.");
		} else {
			reportState = new AgentResponses.ButtonState(true, null);
		}

		return new AgentResponses.ButtonStatesResponse(validateState, legalState, reportState);
	}
}
