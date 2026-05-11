package com.skala.backend.agent.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skala.backend.agent.dto.AgentDtos.AgentType;
import com.skala.backend.agent.dto.AgentDtos.ValidationLogCommand;
import com.skala.backend.agent.dto.SafeleeAgentDtos.EvidenceRequirementInputResponse;
import com.skala.backend.agent.dto.SafeleeAgentDtos.EvidenceRequirementJudgementRequest;
import com.skala.backend.agent.dto.SafeleeAgentDtos.EvidenceRequirementJudgementResponse;
import com.skala.backend.agent.dto.SafeleeAgentDtos.EvidenceRequirementListResponse;
import com.skala.backend.agent.dto.SafeleeAgentDtos.EvidenceRequirementRecord;
import com.skala.backend.agent.repository.AgentRepository;
import com.skala.backend.agent.repository.SafeleeAgentRepository;
import com.skala.backend.auth.security.AuthenticatedUser;
import com.skala.backend.global.error.ApiException;
import com.skala.backend.project.service.ProjectAccessService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class SafeleeAgentService {

	private final ProjectAccessService projectAccessService;
	private final SafeleeAgentRepository safeleeAgentRepository;
	private final AgentRepository agentRepository;
	private final ObjectMapper objectMapper;

	public SafeleeAgentService(
			ProjectAccessService projectAccessService,
			SafeleeAgentRepository safeleeAgentRepository,
			AgentRepository agentRepository,
			ObjectMapper objectMapper
	) {
		this.projectAccessService = projectAccessService;
		this.safeleeAgentRepository = safeleeAgentRepository;
		this.agentRepository = agentRepository;
		this.objectMapper = objectMapper;
	}

	public EvidenceRequirementInputResponse getInput(AuthenticatedUser currentUser, Long projectId, Long statementId, Long itemId) {
		requireAuthenticated(currentUser);
		projectAccessService.requireReadable(currentUser.id(), projectId);
		var itemContext = safeleeAgentRepository.requireItemContext(projectId, statementId, itemId);

		// SafeLee 판단 입력은 Spring이 service DB에서 조립합니다.
		// FastAPI/AI agent는 이 응답을 그대로 판단 입력으로 사용할 수 있습니다.
		return new EvidenceRequirementInputResponse(
				itemContext,
				safeleeAgentRepository.linkedFiles(itemId),
				safeleeAgentRepository.evidenceTypes()
		);
	}

	public EvidenceRequirementJudgementResponse saveJudgement(
			AuthenticatedUser currentUser,
			Long projectId,
			Long statementId,
			Long itemId,
			EvidenceRequirementJudgementRequest request
	) {
		requireAuthenticated(currentUser);
		if (request == null || request.requiredEvidences() == null) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "requiredEvidences는 필수입니다.");
		}
		projectAccessService.requireWritable(currentUser.id(), projectId);
		safeleeAgentRepository.requireItemContext(projectId, statementId, itemId);
		List<String> requiredEvidences = request.requiredEvidences()
				.stream()
				.filter(code -> code != null && !code.isBlank())
				.distinct()
				.toList();
		if (!safeleeAgentRepository.evidenceTypesExist(requiredEvidences)) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "지원하지 않는 증빙 타입이 포함되어 있습니다.");
		}

		// 기존 active requirement를 비활성화하고 새 판단 결과로 교체합니다.
		List<EvidenceRequirementRecord> saved = safeleeAgentRepository.replaceActiveRequirements(itemId, requiredEvidences);
		EvidenceRequirementJudgementResponse response = new EvidenceRequirementJudgementResponse(itemId, saved);
		saveJudgementLog(projectId, statementId, itemId, request, response);
		return response;
	}

	public EvidenceRequirementListResponse listRequirements(AuthenticatedUser currentUser, Long projectId, Long statementId, Long itemId) {
		requireAuthenticated(currentUser);
		projectAccessService.requireReadable(currentUser.id(), projectId);
		safeleeAgentRepository.requireItemContext(projectId, statementId, itemId);
		return new EvidenceRequirementListResponse(itemId, safeleeAgentRepository.activeRequirements(itemId));
	}

	private void saveJudgementLog(
			Long projectId,
			Long statementId,
			Long itemId,
			EvidenceRequirementJudgementRequest request,
			EvidenceRequirementJudgementResponse response
	) {
		Map<String, Object> details = new LinkedHashMap<>();
		details.put("workflow", "evidence_requirement_generation");
		Map<String, Object> requestDetails = new LinkedHashMap<>();
		requestDetails.put("requiredEvidences", request.requiredEvidences());
		requestDetails.put("confidence", request.confidence());
		requestDetails.put("reason", request.reason());
		details.put("request", requestDetails);
		details.put("response", response);
		details.put("error", null);

		agentRepository.saveValidationLog(new ValidationLogCommand(
				projectId,
				statementId,
				itemId,
				"evidence_requirement_generation",
				"generated",
				toJson(details),
				request.modelName(),
				AgentType.SAFETY_DOC.code(),
				"summary",
				"info"
		));
	}

	private void requireAuthenticated(AuthenticatedUser currentUser) {
		if (currentUser == null) {
			throw new ApiException(HttpStatus.UNAUTHORIZED, "인증이 필요합니다.");
		}
	}

	private String toJson(Object value) {
		try {
			return objectMapper.writeValueAsString(value);
		} catch (JsonProcessingException exception) {
			throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "SafeLee 실행 로그를 JSON으로 변환할 수 없습니다.");
		}
	}
}
