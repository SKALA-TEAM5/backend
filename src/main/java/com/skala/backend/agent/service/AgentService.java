package com.skala.backend.agent.service;

import com.skala.backend.agent.client.FastApiAgentClient;
import com.skala.backend.agent.domain.AgentLogStatus;
import com.skala.backend.agent.domain.AgentTypeCode;
import com.skala.backend.agent.dto.AgentRequests;
import com.skala.backend.agent.dto.AgentResponses;
import com.skala.backend.agent.repository.AgentLogRepository;
import com.skala.backend.global.error.ApiException;
import com.skala.backend.project.service.ProjectAccessService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AgentService {

	private final FastApiAgentClient fastApiAgentClient;
	private final ProjectAccessService projectAccessService;
	private final AgentLogRepository agentLogRepository;

	public AgentService(FastApiAgentClient fastApiAgentClient, ProjectAccessService projectAccessService,
			AgentLogRepository agentLogRepository) {
		this.fastApiAgentClient = fastApiAgentClient;
		this.projectAccessService = projectAccessService;
		this.agentLogRepository = agentLogRepository;
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
		Long sid = request.usageStatementId();
		if (!agentLogRepository.existsStatementLogWithSuccessOrHil(sid, AgentTypeCode.SAFETY_DOC.getCode())) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "validate를 먼저 실행해야 합니다.");
		}
		if (agentLogRepository.existsByUsageStatementIdAndAgentTypeCodeAndUsageStatementItemIdIsNull(sid, AgentTypeCode.LINK)
				&& !agentLogRepository.existsStatementLogWithSuccessOrHil(sid, AgentTypeCode.LINK.getCode())) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "link 검증을 통과해야 합니다.");
		}
		if (agentLogRepository.existsByUsageStatementIdAndAgentTypeCodeAndUsageStatementItemIdIsNull(sid, AgentTypeCode.VISION)
				&& !agentLogRepository.existsStatementLogWithSuccessOrHil(sid, AgentTypeCode.VISION.getCode())) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "vision 검증을 통과해야 합니다.");
		}
		if (agentLogRepository.existsByUsageStatementIdAndAgentTypeCodeAndStatusInAndUsageStatementItemIdIsNull(
				sid, AgentTypeCode.LEGAL, List.of(AgentLogStatus.RUNNING, AgentLogStatus.PENDING))) {
			throw new ApiException(HttpStatus.CONFLICT, "현재 실행 중입니다.");
		}
		return fastApiAgentClient.runLegal(projectId, sid, currentUserId);
	}

	public AgentResponses.AgentRunResult report(Long currentUserId, Long projectId, AgentRequests.ReportRequest request) {
		projectAccessService.requireReadable(currentUserId, projectId);
		Long sid = request.usageStatementId();
		if (!agentLogRepository.existsStatementLogWithSuccessOrHil(sid, AgentTypeCode.LEGAL.getCode())) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "legal을 먼저 실행해야 합니다.");
		}
		if (agentLogRepository.existsByUsageStatementIdAndAgentTypeCodeAndStatusInAndUsageStatementItemIdIsNull(
				sid, AgentTypeCode.REPORT, List.of(AgentLogStatus.RUNNING, AgentLogStatus.PENDING))) {
			throw new ApiException(HttpStatus.CONFLICT, "현재 실행 중입니다.");
		}
		return fastApiAgentClient.runReport(projectId, sid, currentUserId);
	}

	public AgentResponses.ButtonStatesResponse getButtonStates(Long currentUserId, Long projectId, Long usageStatementId) {
		projectAccessService.requireReadable(currentUserId, projectId);

		List<AgentLogStatus> running = List.of(AgentLogStatus.RUNNING, AgentLogStatus.PENDING);
		Long sid = usageStatementId;

		// validate
		boolean classiPassed = agentLogRepository.existsStatementLogWithExactResultCode(
				sid, AgentTypeCode.CLASSI.getCode(), "success");
		boolean validateRunning =
				agentLogRepository.existsByUsageStatementIdAndAgentTypeCodeAndStatusInAndUsageStatementItemIdIsNull(sid, AgentTypeCode.SAFETY_DOC, running)
				|| agentLogRepository.existsByUsageStatementIdAndAgentTypeCodeAndStatusInAndUsageStatementItemIdIsNull(sid, AgentTypeCode.LINK, running)
				|| agentLogRepository.existsByUsageStatementIdAndAgentTypeCodeAndStatusInAndUsageStatementItemIdIsNull(sid, AgentTypeCode.VISION, running);

		AgentResponses.ButtonState validateState;
		if (validateRunning) {
			validateState = new AgentResponses.ButtonState(false, "현재 실행 중입니다.");
		} else if (!classiPassed) {
			validateState = new AgentResponses.ButtonState(false, "parse를 먼저 실행해야 합니다.");
		} else {
			validateState = new AgentResponses.ButtonState(true, null);
		}

		// legal
		boolean legalRunning = agentLogRepository
				.existsByUsageStatementIdAndAgentTypeCodeAndStatusInAndUsageStatementItemIdIsNull(sid, AgentTypeCode.LEGAL, running);
		boolean safetyDocPassed = agentLogRepository.existsStatementLogWithSuccessOrHil(sid, AgentTypeCode.SAFETY_DOC.getCode());
		boolean linkExists = agentLogRepository.existsByUsageStatementIdAndAgentTypeCodeAndUsageStatementItemIdIsNull(sid, AgentTypeCode.LINK);
		boolean linkPassed = !linkExists || agentLogRepository.existsStatementLogWithSuccessOrHil(sid, AgentTypeCode.LINK.getCode());
		boolean visionExists = agentLogRepository.existsByUsageStatementIdAndAgentTypeCodeAndUsageStatementItemIdIsNull(sid, AgentTypeCode.VISION);
		boolean visionPassed = !visionExists || agentLogRepository.existsStatementLogWithSuccessOrHil(sid, AgentTypeCode.VISION.getCode());

		AgentResponses.ButtonState legalState;
		if (legalRunning) {
			legalState = new AgentResponses.ButtonState(false, "현재 실행 중입니다.");
		} else if (!safetyDocPassed) {
			legalState = new AgentResponses.ButtonState(false, "validate를 먼저 실행해야 합니다.");
		} else if (!linkPassed || !visionPassed) {
			legalState = new AgentResponses.ButtonState(false, "link 또는 vision 검증을 통과해야 합니다.");
		} else {
			legalState = new AgentResponses.ButtonState(true, null);
		}

		// report
		boolean reportRunning = agentLogRepository
				.existsByUsageStatementIdAndAgentTypeCodeAndStatusInAndUsageStatementItemIdIsNull(sid, AgentTypeCode.REPORT, running);
		boolean legalPassed = agentLogRepository.existsStatementLogWithSuccessOrHil(sid, AgentTypeCode.LEGAL.getCode());

		AgentResponses.ButtonState reportState;
		if (reportRunning) {
			reportState = new AgentResponses.ButtonState(false, "현재 실행 중입니다.");
		} else if (!legalPassed) {
			reportState = new AgentResponses.ButtonState(false, "legal을 먼저 실행해야 합니다.");
		} else {
			reportState = new AgentResponses.ButtonState(true, null);
		}

		return new AgentResponses.ButtonStatesResponse(validateState, legalState, reportState);
	}
}
