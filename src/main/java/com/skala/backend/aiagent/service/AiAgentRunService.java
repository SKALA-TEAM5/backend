package com.skala.backend.aiagent.service;

import com.skala.backend.aiagent.client.AiAgentClient;
import com.skala.backend.aiagent.client.AiAgentClientDtos.AiAgentClientRequest;
import com.skala.backend.aiagent.client.AiAgentClientDtos.AiAgentClientResponse;
import com.skala.backend.aiagent.domain.AiAgentRun;
import com.skala.backend.aiagent.domain.AiAgentRunStatusCode;
import com.skala.backend.aiagent.dto.AiAgentRunRequests.StartAiAgentRunRequest;
import com.skala.backend.aiagent.dto.AiAgentRunResponses.AiAgentRunResponse;
import com.skala.backend.aiagent.repository.AiAgentRunRepository;
import com.skala.backend.global.error.ApiException;
import com.skala.backend.project.domain.Project;
import com.skala.backend.project.service.ProjectAccessService;
import com.skala.backend.user.domain.User;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
public class AiAgentRunService {

	private static final List<AiAgentRunStatusCode> ACTIVE_STATUSES = List.of(
			AiAgentRunStatusCode.REQUESTED,
			AiAgentRunStatusCode.RUNNING
	);

	private final ProjectAccessService projectAccessService;
	private final AiAgentRunRepository aiAgentRunRepository;
	private final AiAgentContextService contextService;
	private final AiAgentClient aiAgentClient;
	private final ValidationLogWriter validationLogWriter;

	public AiAgentRunService(
			ProjectAccessService projectAccessService,
			AiAgentRunRepository aiAgentRunRepository,
			AiAgentContextService contextService,
			AiAgentClient aiAgentClient,
			ValidationLogWriter validationLogWriter
	) {
		this.projectAccessService = projectAccessService;
		this.aiAgentRunRepository = aiAgentRunRepository;
		this.contextService = contextService;
		this.aiAgentClient = aiAgentClient;
		this.validationLogWriter = validationLogWriter;
	}

	@Transactional
	public AiAgentRunResponse run(Long currentUserId, Long projectId, StartAiAgentRunRequest request) {
		Project project = projectAccessService.requireReadable(currentUserId, projectId);
		User currentUser = projectAccessService.requireCurrentUser(currentUserId);
		if (aiAgentRunRepository.existsByProjectIdAndAgentTypeCodeAndStatusCodeIn(
				projectId,
				request.agentTypeCode(),
				ACTIVE_STATUSES
		)) {
			throw new ApiException(HttpStatus.CONFLICT, "이미 실행 중인 AI Agent 작업이 있습니다.");
		}

		Map<String, Object> context = contextService.build(project, request);
		AiAgentRun run = saveRequestedRun(projectId, currentUserId, request);
		run.start();

		try {
			AiAgentClientResponse clientResponse = aiAgentClient.run(new AiAgentClientRequest(
					run.getId(),
					projectId,
					run.getAgentTypeCode(),
					currentUserId,
					currentUser.getRoleCode(),
					context
			));
			int resultCount = validationLogWriter.writeResults(run, clientResponse.results());
			if (clientResponse.statusCode() == AiAgentRunStatusCode.FAILED) {
				run.fail(clientResponse.errorMessage() == null ? "AI Agent 실행에 실패했습니다." : clientResponse.errorMessage());
			} else {
				run.complete();
			}
			aiAgentRunRepository.saveAndFlush(run);
			return toResponse(run, resultCount);
		} catch (RuntimeException exception) {
			run.fail(exception.getMessage());
			validationLogWriter.writeError(run, exception.getMessage());
			aiAgentRunRepository.saveAndFlush(run);
			return toResponse(run, 1);
		}
	}

	private AiAgentRun saveRequestedRun(Long projectId, Long currentUserId, StartAiAgentRunRequest request) {
		try {
			return aiAgentRunRepository.saveAndFlush(AiAgentRun.request(
					projectId,
					currentUserId,
					request.agentTypeCode()
			));
		} catch (DataIntegrityViolationException exception) {
			throw new ApiException(HttpStatus.CONFLICT, "이미 실행 중인 AI Agent 작업이 있습니다.");
		}
	}

	private AiAgentRunResponse toResponse(AiAgentRun run, int resultCount) {
		return new AiAgentRunResponse(
				run.getId(),
				run.getProjectId(),
				run.getAgentTypeCode(),
				run.getStatusCode(),
				resultCount
		);
	}
}
