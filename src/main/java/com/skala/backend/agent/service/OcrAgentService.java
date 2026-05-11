package com.skala.backend.agent.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skala.backend.agent.client.FastApiAgentClient;
import com.skala.backend.agent.dto.AgentDtos.AgentType;
import com.skala.backend.agent.dto.AgentDtos.ValidationLogCommand;
import com.skala.backend.agent.dto.OcrAgentDtos.OcrEvidenceMatchRequest;
import com.skala.backend.agent.dto.OcrAgentDtos.OcrUsageStatementParseRequest;
import com.skala.backend.agent.dto.OcrAgentDtos.OcrWorkflowResponse;
import com.skala.backend.agent.repository.AgentRepository;
import com.skala.backend.agent.repository.OcrAgentRepository;
import com.skala.backend.auth.security.AuthenticatedUser;
import com.skala.backend.file.domain.ProjectFile;
import com.skala.backend.global.error.ApiException;
import com.skala.backend.project.service.ProjectAccessService;
import com.skala.backend.usage.domain.UsageStatement;
import com.skala.backend.usage.domain.UsageStatementItem;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class OcrAgentService {

	private final ProjectAccessService projectAccessService;
	private final AgentRepository agentRepository;
	private final OcrAgentRepository ocrAgentRepository;
	private final FastApiAgentClient fastApiAgentClient;
	private final ObjectMapper objectMapper;

	public OcrAgentService(
			ProjectAccessService projectAccessService,
			AgentRepository agentRepository,
			OcrAgentRepository ocrAgentRepository,
			FastApiAgentClient fastApiAgentClient,
			ObjectMapper objectMapper
	) {
		this.projectAccessService = projectAccessService;
		this.agentRepository = agentRepository;
		this.ocrAgentRepository = ocrAgentRepository;
		this.fastApiAgentClient = fastApiAgentClient;
		this.objectMapper = objectMapper;
	}

	public OcrWorkflowResponse parseUsageStatement(
			AuthenticatedUser currentUser,
			Long projectId,
			OcrUsageStatementParseRequest request
	) {
		// 사용내역서 업로드는 우선 돌아가는 흐름을 위해 "OCR -> DB 적재 -> classification" 순서로 처리합니다.
		// classification 실패 여부는 validation_logs로 판단하고, 이미 적재한 row는 보존합니다.
		requireAuthenticated(currentUser);
		projectAccessService.requireWritable(currentUser.id(), projectId);
		ProjectFile file = ocrAgentRepository.requireFile(projectId, request.fileId());
		requireFileType(file, "usage_statement");

		String requestId = UUID.randomUUID().toString();
		Map<String, Object> parseResponse;
		try {
			parseResponse = fastApiAgentClient.parseOcr(fileRecord(file));
		} catch (ApiException exception) {
			Long logId = saveOcrErrorLog(projectId, null, null, "ocr_usage_statement_parse", requestId, "usage_statement_upload", file, exception);
			throw new ApiException(exception.getStatus(), exception.getMessage() + " validationLogId=" + logId);
		}

		Long logId = saveOcrLog(
				projectId,
				null,
				null,
				"ocr_usage_statement_parse",
				requestId,
				"usage_statement_upload",
				"parse",
				file,
				parseResponse
		);

		if (!isSuccess(parseResponse)) {
			// OCR 자체가 비즈니스 실패(success=false)인 경우에는 DB 적재와 classification을 진행하지 않습니다.
			return new OcrWorkflowResponse(
					requestId,
					"usage_statement_upload",
					ocrStatus(parseResponse),
					List.of(logId),
					null,
					null,
					parseResponse
			);
		}

		Map<String, Object> parseData = parseData(parseResponse);
		Map<String, Object> savedStatement = ocrAgentRepository.saveParsedUsageStatement(projectId, file.getId(), parseData);
		Long usageStatementId = (Long) savedStatement.get("usageStatementId");
		Map<String, Object> classificationRequest = classificationRequest(usageStatementId, savedStatement);
		Map<String, Object> classificationResponse;

		try {
			classificationResponse = fastApiAgentClient.runClassification(projectId, usageStatementId, classificationRequest);
		} catch (ApiException exception) {
			Long classificationErrorLogId = saveClassificationErrorLog(projectId, usageStatementId, requestId, file, exception);
			throw new ApiException(exception.getStatus(), exception.getMessage() + " validationLogId=" + classificationErrorLogId);
		}

		Long classificationLogId = saveClassificationLog(projectId, usageStatementId, requestId, file, classificationRequest, classificationResponse);
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("parse", parseResponse);
		result.put("saved_usage_statement", savedStatement);
		result.put("classification", classificationResponse);

		return new OcrWorkflowResponse(
				requestId,
				"usage_statement_upload",
				classificationResultCode(classificationResponse),
				List.of(logId, classificationLogId),
				usageStatementId,
				null,
				result
		);
	}

	public OcrWorkflowResponse parseAndMatchEvidence(
			AuthenticatedUser currentUser,
			Long projectId,
			OcrEvidenceMatchRequest request
	) {
		// 증빙 업로드는 이미 존재하는 usage_statement_item을 기준으로 OCR 결과를 매칭합니다.
		// matched일 때만 evidence_file_links를 생성하고, 나머지는 validation_logs에 판단 결과만 남깁니다.
		requireAuthenticated(currentUser);
		projectAccessService.requireWritable(currentUser.id(), projectId);
		UsageStatement usageStatement = ocrAgentRepository.requireUsageStatement(projectId, request.usageStatementId());
		UsageStatementItem item = requireRequestedItem(projectId, usageStatement, request.usageStatementItemId());
		ProjectFile file = ocrAgentRepository.requireFile(projectId, request.fileId());
		rejectUsageStatementFile(file);

		String requestId = UUID.randomUUID().toString();
		Map<String, Object> parseResponse;
		try {
			parseResponse = fastApiAgentClient.parseOcr(fileRecord(file));
		} catch (ApiException exception) {
			Long logId = saveOcrErrorLog(projectId, usageStatement.getId(), item.getId(), "ocr_evidence_parse", requestId, "evidence_upload", file, exception);
			throw new ApiException(exception.getStatus(), exception.getMessage() + " validationLogId=" + logId);
		}

		Long parseLogId = saveOcrLog(
				projectId,
				usageStatement.getId(),
				item.getId(),
				"ocr_evidence_parse",
				requestId,
				"evidence_upload",
				"parse",
				file,
				parseResponse
		);

		if (!isSuccess(parseResponse)) {
			return new OcrWorkflowResponse(
					requestId,
					"evidence_upload",
					"parse_failed",
					List.of(parseLogId),
					usageStatement.getId(),
					null,
					parseResponse
			);
		}

		Map<String, Object> matchingRequest = matchingRequest(projectId, usageStatement.getId(), item, parseResponse);
		Map<String, Object> matchingResponse;
		try {
			matchingResponse = fastApiAgentClient.runMatching(matchingRequest);
		} catch (ApiException exception) {
			Long matchErrorLogId = saveOcrErrorLog(projectId, usageStatement.getId(), item.getId(), "ocr_receipt_match", requestId, "evidence_upload", file, exception);
			throw new ApiException(exception.getStatus(), exception.getMessage() + " validationLogId=" + matchErrorLogId);
		}

		Long matchLogId = saveMatchingLog(projectId, usageStatement.getId(), item.getId(), requestId, file, matchingRequest, matchingResponse);
		Long linkId = null;
		if ("matched".equals(matchStatus(matchingResponse))) {
			linkId = ocrAgentRepository.linkEvidenceFile(item, file);
		}

		Map<String, Object> result = new LinkedHashMap<>();
		result.put("parse", parseResponse);
		result.put("matching", matchingResponse);

		return new OcrWorkflowResponse(
				requestId,
				"evidence_upload",
				matchStatus(matchingResponse),
				List.of(parseLogId, matchLogId),
				usageStatement.getId(),
				linkId,
				result
		);
	}

	private UsageStatementItem requireRequestedItem(Long projectId, UsageStatement usageStatement, Long itemId) {
		UsageStatementItem item = ocrAgentRepository.requireProjectItem(projectId, itemId);
		if (!usageStatement.getId().equals(item.getUsageStatementId())) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "usageStatementItemId가 usageStatementId에 속하지 않습니다.");
		}
		return item;
	}

	private void requireAuthenticated(AuthenticatedUser currentUser) {
		if (currentUser == null) {
			throw new ApiException(HttpStatus.UNAUTHORIZED, "인증이 필요합니다.");
		}
	}

	private void requireFileType(ProjectFile file, String evidenceTypeCode) {
		if (!evidenceTypeCode.equals(file.getUploadedEvidenceTypeCode())) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "파일 증빙 유형이 올바르지 않습니다.");
		}
	}

	private void rejectUsageStatementFile(ProjectFile file) {
		if ("usage_statement".equals(file.getUploadedEvidenceTypeCode())) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "사용내역서 파일은 증빙 OCR 매칭 대상이 아닙니다.");
		}
	}

	private Map<String, Object> fileRecord(ProjectFile file) {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("id", file.getId());
		payload.put("project_id", file.getProjectId());
		payload.put("uploaded_by_user_id", file.getUploadedByUserId());
		payload.put("uploaded_evidence_type_code", file.getUploadedEvidenceTypeCode());
		payload.put("original_filename", file.getOriginalFilename());
		payload.put("storage_key", file.getStorageKey());
		payload.put("mime_type", file.getMimeType());
		payload.put("size_bytes", file.getSizeBytes());
		payload.put("captured_at", file.getCapturedAt());
		payload.put("uploaded_at", file.getUploadedAt());
		return payload;
	}

	private Map<String, Object> matchingRequest(Long projectId, Long usageStatementId, UsageStatementItem item, Map<String, Object> parseResponse) {
		// FastAPI matching API는 OCR이 끝난 receipt_ocr_results를 입력으로 받습니다.
		// 그래서 Spring이 /ocr/parse 응답을 바로 /matching/run 요청 형태로 이어 붙입니다.
		return Map.of(
				"project_id", projectId,
				"usage_statement_id", usageStatementId,
				"save_to_db", false,
				"usage_items", List.of(usageItemForMatching(item)),
				"receipt_ocr_results", List.of(ocrResultForMatching(parseResponse)),
				"photo_texts", Map.of()
		);
	}

	private Map<String, Object> usageItemForMatching(UsageStatementItem item) {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("seq", item.getId());
		payload.put("category_code", item.getCategoryCode());
		payload.put("used_on", item.getUsedOn());
		payload.put("item_name", item.getItemName());
		payload.put("total_amount", item.getTotalAmount());
		payload.put("remark", item.getRemark());
		return payload;
	}

	private Object ocrResultForMatching(Map<String, Object> parseResponse) {
		Object data = parseResponse.get("data");
		if (data instanceof Map<?, ?> dataMap && dataMap.get("ocr_result") != null) {
			return dataMap.get("ocr_result");
		}
		return data == null ? Map.of() : data;
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> parseData(Map<String, Object> parseResponse) {
		Object data = parseResponse.get("data");
		if (data instanceof Map<?, ?> map) {
			return (Map<String, Object>) map;
		}
		throw new ApiException(HttpStatus.BAD_GATEWAY, "사용내역서 OCR 결과 data가 올바르지 않습니다.");
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> classificationRequest(Long usageStatementId, Map<String, Object> savedStatement) {
		return Map.of(
				"usageStatementId", usageStatementId.toString(),
				"rerun", false,
				"lineItems", (List<Map<String, Object>>) savedStatement.getOrDefault("lineItems", List.of())
		);
	}

	private Long saveOcrLog(
			Long projectId,
			Long usageStatementId,
			Long usageStatementItemId,
			String validationTypeCode,
			String requestId,
			String workflow,
			String step,
			ProjectFile file,
			Map<String, Object> response
	) {
		// OCR parse 결과는 별도 OCR 결과 테이블을 만들지 않고 validation_logs.details에 그대로 보존합니다.
		Map<String, Object> details = new LinkedHashMap<>();
		details.put("request_id", requestId);
		details.put("workflow", workflow);
		details.put("step", step);
		details.put("request", Map.of("file_id", file.getId()));
		details.put("response", response);
		details.put("error", response.get("error"));

		return agentRepository.saveValidationLog(new ValidationLogCommand(
				projectId,
				usageStatementId,
				usageStatementItemId,
				validationTypeCode,
				isSuccess(response) ? "parsed" : "error",
				toJson(details),
				"fastapi_ocr",
				AgentType.OCR.code(),
				isSuccess(response) ? "summary" : "error",
				isSuccess(response) ? "info" : "high"
		));
	}

	private Long saveMatchingLog(
			Long projectId,
			Long usageStatementId,
			Long usageStatementItemId,
			String requestId,
			ProjectFile file,
			Map<String, Object> matchingRequest,
			Map<String, Object> matchingResponse
	) {
		Map<String, Object> details = new LinkedHashMap<>();
		details.put("request_id", requestId);
		details.put("workflow", "evidence_upload");
		details.put("step", "matching");
		details.put("request", Map.of(
				"file_id", file.getId(),
				"usage_statement_id", usageStatementId,
				"usage_statement_item_id", usageStatementItemId,
				"matching", matchingRequest
		));
		details.put("response", matchingResponse);
		details.put("error", null);

		String matchStatus = matchStatus(matchingResponse);
		return agentRepository.saveValidationLog(new ValidationLogCommand(
				projectId,
				usageStatementId,
				usageStatementItemId,
				"ocr_receipt_match",
				matchStatus,
				toJson(details),
				"matching_service_monthly_v1",
				AgentType.OCR.code(),
				"summary",
				"matched".equals(matchStatus) ? "info" : "medium"
		));
	}

	private Long saveClassificationLog(
			Long projectId,
			Long usageStatementId,
			String requestId,
			ProjectFile file,
			Map<String, Object> classificationRequest,
			Map<String, Object> classificationResponse
	) {
		// classification은 사용내역서가 DB에 생성된 뒤 실행되므로 usage_statement_id를 채워 저장합니다.
		// line item별 상세 결과는 response 전체를 details에 보존하고, summary result_code만 컬럼에 복사합니다.
		Map<String, Object> details = new LinkedHashMap<>();
		details.put("request_id", requestId);
		details.put("workflow", "usage_statement_upload");
		details.put("step", "classification");
		details.put("request", Map.of(
				"file_id", file.getId(),
				"usage_statement_id", usageStatementId,
				"classification", classificationRequest
		));
		details.put("response", classificationResponse);
		details.put("error", null);

		String resultCode = classificationResultCode(classificationResponse);
		return agentRepository.saveValidationLog(new ValidationLogCommand(
				projectId,
				usageStatementId,
				null,
				"classification",
				resultCode,
				toJson(details),
				"classifier_agent",
				AgentType.CLASSIFIER.code(),
				"summary",
				"pass".equals(resultCode) ? "info" : "medium"
		));
	}

	private Long saveClassificationErrorLog(
			Long projectId,
			Long usageStatementId,
			String requestId,
			ProjectFile file,
			ApiException exception
	) {
		Map<String, Object> details = new LinkedHashMap<>();
		details.put("request_id", requestId);
		details.put("workflow", "usage_statement_upload");
		details.put("step", "classification");
		details.put("request", Map.of(
				"file_id", file.getId(),
				"usage_statement_id", usageStatementId
		));
		details.put("response", Map.of());
		details.put("error", Map.of(
				"status", exception.getStatus().value(),
				"message", exception.getMessage()
		));

		return agentRepository.saveValidationLog(new ValidationLogCommand(
				projectId,
				usageStatementId,
				null,
				"classification",
				"error",
				toJson(details),
				null,
				AgentType.CLASSIFIER.code(),
				"error",
				"high"
		));
	}

	private Long saveOcrErrorLog(
			Long projectId,
			Long usageStatementId,
			Long usageStatementItemId,
			String validationTypeCode,
			String requestId,
			String workflow,
			ProjectFile file,
			ApiException exception
	) {
		Map<String, Object> details = new LinkedHashMap<>();
		details.put("request_id", requestId);
		details.put("workflow", workflow);
		details.put("request", Map.of("file_id", file.getId()));
		details.put("response", Map.of());
		details.put("error", Map.of(
				"status", exception.getStatus().value(),
				"message", exception.getMessage()
		));

		return agentRepository.saveValidationLog(new ValidationLogCommand(
				projectId,
				usageStatementId,
				usageStatementItemId,
				validationTypeCode,
				"error",
				toJson(details),
				null,
				AgentType.OCR.code(),
				"error",
				"high"
		));
	}

	private boolean isSuccess(Map<String, Object> response) {
		return Boolean.TRUE.equals(response.get("success"));
	}

	private String ocrStatus(Map<String, Object> response) {
		return isSuccess(response) ? "parsed" : "error";
	}

	private String matchStatus(Map<String, Object> matchingResponse) {
		Object results = matchingResponse.get("results");
		if (results instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof Map<?, ?> first) {
			Object status = first.get("match_status");
			if (status instanceof String value) {
				return value;
			}
		}
		Object summary = matchingResponse.get("summary");
		if (summary instanceof Map<?, ?> summaryMap && numberValue(summaryMap.get("matched")) > 0) {
			return "matched";
		}
		return "unmatched";
	}

	private String classificationResultCode(Map<String, Object> classificationResponse) {
		// 우선 단순 정책으로 갑니다. 모든 항목이 keep이고 human review가 없으면 pass, 하나라도 변경/검토면 review_needed.
		Object lineItems = classificationResponse.get("lineItems");
		if (!(lineItems instanceof List<?> items) || items.isEmpty()) {
			return "review_needed";
		}
		for (Object item : items) {
			if (!(item instanceof Map<?, ?> map)) {
				return "review_needed";
			}
			Object decisionStatus = map.get("decisionStatus");
			Object needsHumanReview = map.get("needsHumanReview");
			if (Boolean.TRUE.equals(needsHumanReview) || !"keep".equals(decisionStatus)) {
				return "review_needed";
			}
		}
		return "pass";
	}

	private int numberValue(Object value) {
		return value instanceof Number number ? number.intValue() : 0;
	}

	private String toJson(Object value) {
		try {
			return objectMapper.writeValueAsString(value);
		} catch (JsonProcessingException exception) {
			throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "OCR 실행 로그를 JSON으로 변환할 수 없습니다.");
		}
	}
}
