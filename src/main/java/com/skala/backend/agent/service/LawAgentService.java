package com.skala.backend.agent.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skala.backend.agent.client.FastApiAgentClient;
import com.skala.backend.agent.dto.AgentDtos.AgentType;
import com.skala.backend.agent.dto.AgentDtos.ValidationLogCommand;
import com.skala.backend.agent.dto.LawAgentDtos.ClassificationRunRequest;
import com.skala.backend.agent.dto.LawAgentDtos.LawAgentRunResponse;
import com.skala.backend.agent.dto.LawAgentDtos.ValidationConfirmRequest;
import com.skala.backend.agent.dto.LawAgentDtos.ValidationRunRequest;
import com.skala.backend.agent.repository.AgentRepository;
import com.skala.backend.agent.repository.LawAgentRepository;
import com.skala.backend.auth.security.AuthenticatedUser;
import com.skala.backend.global.error.ApiException;
import com.skala.backend.project.domain.Project;
import com.skala.backend.project.service.ProjectAccessService;
import com.skala.backend.usage.domain.UsageStatement;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class LawAgentService {

	private final ProjectAccessService projectAccessService;
	private final LawAgentRepository lawAgentRepository;
	private final AgentRepository agentRepository;
	private final FastApiAgentClient fastApiAgentClient;
	private final ObjectMapper objectMapper;

	public LawAgentService(
			ProjectAccessService projectAccessService,
			LawAgentRepository lawAgentRepository,
			AgentRepository agentRepository,
			FastApiAgentClient fastApiAgentClient,
			ObjectMapper objectMapper
	) {
		this.projectAccessService = projectAccessService;
		this.lawAgentRepository = lawAgentRepository;
		this.agentRepository = agentRepository;
		this.fastApiAgentClient = fastApiAgentClient;
		this.objectMapper = objectMapper;
	}

	public LawAgentRunResponse runClassification(
			AuthenticatedUser currentUser,
			Long projectId,
			Long statementId,
			ClassificationRunRequest request
	) {
		// 프론트는 rerun 같은 실행 옵션만 보냅니다.
		// FastAPI에 필요한 lineItems는 Spring이 현재 DB 상태에서 항상 조립합니다.
		requireAuthenticated(currentUser);
		projectAccessService.requireReadable(currentUser.id(), projectId);
		UsageStatement statement = lawAgentRepository.requireUsageStatement(projectId, statementId);
		Map<String, Object> payload = classificationPayload(statement, request);
		Map<String, Object> response = fastApiAgentClient.runClassification(projectId, statementId, payload);
		Long logId = saveLawLog(projectId, statementId, null, "classification", AgentType.CLASSIFIER, classificationResultCode(response), payload, response);
		return new LawAgentRunResponse("classification", classificationResultCode(response), List.of(logId), response);
	}

	public LawAgentRunResponse runValidation(
			AuthenticatedUser currentUser,
			Long projectId,
			ValidationRunRequest request
	) {
		// validation도 동일하게 DB 기준으로 payload를 조립합니다.
		// 프론트가 금액/항목 데이터를 다시 보내면 DB와 불일치할 수 있으므로 받지 않습니다.
		requireAuthenticated(currentUser);
		if (request == null) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "요청 본문은 필수입니다.");
		}
		Project project = projectAccessService.requireReadable(currentUser.id(), projectId);
		Long statementId = parseRequiredLong(request.usageStatementId(), "usageStatementId");
		UsageStatement statement = lawAgentRepository.requireUsageStatement(projectId, statementId);
		Map<String, Object> payload = validationPayload(project, statement, request);
		Map<String, Object> response = fastApiAgentClient.runValidation(projectId, payload);
		Long logId = saveLawLog(projectId, statementId, null, "law", AgentType.VALIDATOR, validationResultCode(response), payload, response);
		return new LawAgentRunResponse("validation", validationResultCode(response), List.of(logId), response);
	}

	public Map<String, Object> getClassificationStatus(AuthenticatedUser currentUser, Long projectId, Long statementId, String classificationId) {
		requireAuthenticated(currentUser);
		projectAccessService.requireReadable(currentUser.id(), projectId);
		lawAgentRepository.requireUsageStatement(projectId, statementId);
		return fastApiAgentClient.getClassificationStatus(projectId, statementId, classificationId);
	}

	public Map<String, Object> getLatestClassification(AuthenticatedUser currentUser, Long projectId, Long statementId) {
		requireAuthenticated(currentUser);
		projectAccessService.requireReadable(currentUser.id(), projectId);
		lawAgentRepository.requireUsageStatement(projectId, statementId);
		return fastApiAgentClient.getLatestClassification(projectId, statementId);
	}

	public Map<String, Object> getValidationStatus(AuthenticatedUser currentUser, Long projectId, String validationId) {
		requireAuthenticated(currentUser);
		projectAccessService.requireReadable(currentUser.id(), projectId);
		return fastApiAgentClient.getValidationStatus(projectId, validationId);
	}

	public Map<String, Object> getLatestValidation(AuthenticatedUser currentUser, Long projectId) {
		requireAuthenticated(currentUser);
		projectAccessService.requireReadable(currentUser.id(), projectId);
		return fastApiAgentClient.getLatestValidation(projectId);
	}

	public Map<String, Object> confirmValidation(AuthenticatedUser currentUser, Long projectId, String validationId, ValidationConfirmRequest request) {
		requireAuthenticated(currentUser);
		if (request == null || request.decision() == null || request.decision().isBlank()) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "decision은 필수입니다.");
		}
		projectAccessService.requireWritable(currentUser.id(), projectId);
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("decision", request.decision());
		payload.put("comment", request.comment());
		return fastApiAgentClient.confirmValidation(projectId, validationId, payload);
	}

	private Map<String, Object> classificationPayload(UsageStatement statement, ClassificationRunRequest request) {
		return Map.of(
				"usageStatementId", statement.getId().toString(),
				"rerun", request != null && request.rerunOrFalse(),
				"lineItems", lawAgentRepository.classificationLineItems(statement.getId())
		);
	}

	private Map<String, Object> validationPayload(Project project, UsageStatement statement, ValidationRunRequest request) {
		return Map.of(
				"usageStatementId", statement.getId().toString(),
				"rerun", request.rerunOrFalse(),
				"basicInfo", Map.of(
						"baseAmount", project.getAppropriatedAmount(),
						"progressRate", statement.getCumulativeProgressRate()
				),
				"categories", lawAgentRepository.validationCategories(statement.getId())
		);
	}

	private Long saveLawLog(
			Long projectId,
			Long statementId,
			Long itemId,
			String validationTypeCode,
			AgentType agentType,
			String resultCode,
			Map<String, Object> request,
			Map<String, Object> response
	) {
		// Law agent 결과도 다른 agent와 동일하게 validation_logs에 단일 summary row로 저장합니다.
		// 상세 응답은 details.response에 보존하고, 목록 필터용 result_code만 컬럼에 복사합니다.
		Map<String, Object> details = new LinkedHashMap<>();
		details.put("workflow", validationTypeCode);
		details.put("request", request);
		details.put("response", response);
		details.put("error", null);
		return agentRepository.saveValidationLog(new ValidationLogCommand(
				projectId,
				statementId,
				itemId,
				validationTypeCode,
				resultCode,
				toJson(details),
				agentType == AgentType.CLASSIFIER ? "classifier_agent" : "validator_agent",
				agentType.code(),
				"summary",
				"pass".equals(resultCode) ? "info" : "medium"
		));
	}

	private String classificationResultCode(Map<String, Object> response) {
		// 모든 항목이 keep이면 pass, 하나라도 changed/needs_review면 review_needed로 둡니다.
		Object lineItems = response.get("lineItems");
		if (!(lineItems instanceof List<?> items) || items.isEmpty()) {
			return "review_needed";
		}
		for (Object item : items) {
			if (!(item instanceof Map<?, ?> map)) {
				return "review_needed";
			}
			if (Boolean.TRUE.equals(map.get("needsHumanReview")) || !"keep".equals(map.get("decisionStatus"))) {
				return "review_needed";
			}
		}
		return "pass";
	}

	private String validationResultCode(Map<String, Object> response) {
		// 모든 category result.status가 appropriate이면 pass로 봅니다.
		Object categories = response.get("categories");
		if (!(categories instanceof List<?> list) || list.isEmpty()) {
			return "review_needed";
		}
		for (Object category : list) {
			if (category instanceof Map<?, ?> map && map.get("result") instanceof Map<?, ?> result) {
				Object status = result.get("status");
				if (!"appropriate".equals(status)) {
					return "review_needed";
				}
			}
		}
		return "pass";
	}

	private void requireAuthenticated(AuthenticatedUser currentUser) {
		if (currentUser == null) {
			throw new ApiException(HttpStatus.UNAUTHORIZED, "인증이 필요합니다.");
		}
	}

	private Long parseRequiredLong(String value, String fieldName) {
		if (value == null || value.isBlank()) {
			throw new ApiException(HttpStatus.BAD_REQUEST, fieldName + "는 필수입니다.");
		}
		try {
			return Long.parseLong(value);
		} catch (NumberFormatException exception) {
			throw new ApiException(HttpStatus.BAD_REQUEST, fieldName + "는 숫자여야 합니다.");
		}
	}

	private String toJson(Object value) {
		try {
			return objectMapper.writeValueAsString(value);
		} catch (JsonProcessingException exception) {
			throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Law agent 실행 로그를 JSON으로 변환할 수 없습니다.");
		}
	}
}
