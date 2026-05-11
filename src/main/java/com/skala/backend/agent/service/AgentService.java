package com.skala.backend.agent.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skala.backend.agent.client.FastApiAgentClient;
import com.skala.backend.agent.dto.AgentDtos.AgentRunRequest;
import com.skala.backend.agent.dto.AgentDtos.AgentRunResponse;
import com.skala.backend.agent.dto.AgentDtos.AgentType;
import com.skala.backend.agent.dto.AgentDtos.FastApiAgentRequest;
import com.skala.backend.agent.dto.AgentDtos.FastApiAgentResponse;
import com.skala.backend.agent.dto.AgentDtos.ValidationLogCommand;
import com.skala.backend.agent.repository.AgentRepository;
import com.skala.backend.auth.security.AuthenticatedUser;
import com.skala.backend.global.error.ApiException;
import com.skala.backend.project.domain.Project;
import com.skala.backend.project.service.ProjectAccessService;
import com.skala.backend.usage.domain.UsageStatement;
import com.skala.backend.usage.domain.UsageStatementItem;
import com.skala.backend.usage.domain.UsageStatementSummary;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AgentService {

	private static final String INPUT_VERSION = "v1";

	private final ProjectAccessService projectAccessService;
	private final AgentRepository agentRepository;
	private final FastApiAgentClient fastApiAgentClient;
	private final ObjectMapper objectMapper;

	public AgentService(
			ProjectAccessService projectAccessService,
			AgentRepository agentRepository,
			FastApiAgentClient fastApiAgentClient,
			ObjectMapper objectMapper
	) {
		this.projectAccessService = projectAccessService;
		this.agentRepository = agentRepository;
		this.fastApiAgentClient = fastApiAgentClient;
		this.objectMapper = objectMapper;
	}

	// Generic agent 경로입니다. DB에 이미 존재하는 사용내역서 context를 조립해 FastAPI agent로 보냅니다.
	// OCR 전용 workflow는 OcrAgentService에 분리되어 있어 이 서비스는 공통 agent 실행만 담당합니다.
	public AgentRunResponse run(
			AuthenticatedUser currentUser,
			Long projectId,
			String agentTypeValue,
			AgentRunRequest request
	) {
		if (currentUser == null) {
			throw new ApiException(HttpStatus.UNAUTHORIZED, "인증이 필요합니다.");
		}

		AgentType agentType = AgentType.from(agentTypeValue);
		Project project = projectAccessService.requireReadable(currentUser.id(), projectId);
		UsageStatement usageStatement = agentRepository.requireUsageStatement(projectId, request.usageStatementId());
		UsageStatementItem requestedItem = requireRequestedItem(projectId, usageStatement, request.usageStatementItemId());
		String requestId = UUID.randomUUID().toString();
		Map<String, Object> context = buildContext(project, usageStatement, requestedItem);
		FastApiAgentRequest fastApiRequest = new FastApiAgentRequest(
				requestId,
				agentType.code(),
				INPUT_VERSION,
				context,
				request.optionsOrEmpty()
		);

		try {
			FastApiAgentResponse fastApiResponse = fastApiAgentClient.run(agentType, fastApiRequest);
			Long validationLogId = saveSuccessLog(projectId, usageStatement, requestedItem, agentType, request, context, fastApiResponse);
			return new AgentRunResponse(
					requestId,
					agentType.code(),
					fastApiResponse.status(),
					List.of(validationLogId),
					fastApiResponse.result() == null ? Map.of() : fastApiResponse.result()
			);
		} catch (ApiException exception) {
			saveErrorLog(projectId, usageStatement, requestedItem, agentType, requestId, request, context, exception);
			throw exception;
		}
	}

	private UsageStatementItem requireRequestedItem(Long projectId, UsageStatement usageStatement, Long itemId) {
		if (itemId == null) {
			return null;
		}
		UsageStatementItem item = agentRepository.requireProjectItem(projectId, itemId);
		if (!usageStatement.getId().equals(item.getUsageStatementId())) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "usageStatementItemId가 usageStatementId에 속하지 않습니다.");
		}
		return item;
	}

	private Map<String, Object> buildContext(Project project, UsageStatement usageStatement, UsageStatementItem requestedItem) {
		List<UsageStatementSummary> summaries = agentRepository.findUsageSummaries(usageStatement.getId());
		List<UsageStatementItem> items = requestedItem == null
				? agentRepository.findUsageItems(usageStatement.getId())
				: List.of(requestedItem);

		return Map.of(
				"project", projectContext(project),
				"usage_statement", usageStatementContext(usageStatement),
				"summaries", summaries.stream().map(this::summaryContext).toList(),
				"items", items.stream().map(this::itemContext).toList()
		);
	}

	private Map<String, Object> projectContext(Project project) {
		Map<String, Object> context = new LinkedHashMap<>();
		context.put("id", project.getId());
		context.put("contract_no", project.getContractNo());
		context.put("construction_company", project.getConstructionCompany());
		context.put("project_name", project.getProjectName());
		context.put("site_location", project.getSiteLocation());
		context.put("contract_amount", project.getContractAmount());
		context.put("appropriated_amount", project.getAppropriatedAmount());
		context.put("construction_start_date", project.getConstructionStartDate());
		context.put("construction_end_date", project.getConstructionEndDate());
		return context;
	}

	private Map<String, Object> usageStatementContext(UsageStatement usageStatement) {
		Map<String, Object> context = new LinkedHashMap<>();
		context.put("id", usageStatement.getId());
		context.put("project_id", usageStatement.getProjectId());
		context.put("report_month", usageStatement.getReportMonth());
		context.put("revision_no", usageStatement.getRevisionNo());
		context.put("document_written_date", usageStatement.getDocumentWrittenDate());
		context.put("cumulative_progress_rate", usageStatement.getCumulativeProgressRate());
		return context;
	}

	private Map<String, Object> summaryContext(UsageStatementSummary summary) {
		Map<String, Object> context = new LinkedHashMap<>();
		context.put("id", summary.getId());
		context.put("category_code", summary.getCategoryCode());
		context.put("previous_amount", summary.getPreviousAmount());
		context.put("current_amount", summary.getCurrentAmount());
		context.put("cumulative_amount", summary.getCumulativeAmount());
		return context;
	}

	private Map<String, Object> itemContext(UsageStatementItem item) {
		Map<String, Object> context = new LinkedHashMap<>();
		context.put("id", item.getId());
		context.put("category_code", item.getCategoryCode());
		context.put("used_on", item.getUsedOn());
		context.put("item_name", item.getItemName());
		context.put("unit", item.getUnit());
		context.put("quantity", item.getQuantity());
		context.put("unit_price", item.getUnitPrice());
		context.put("total_amount", item.getTotalAmount());
		context.put("remark", item.getRemark());
		context.put("page_no", item.getPageNo());
		return context;
	}

	private Long saveSuccessLog(
			Long projectId,
			UsageStatement usageStatement,
			UsageStatementItem requestedItem,
			AgentType agentType,
			AgentRunRequest request,
			Map<String, Object> context,
			FastApiAgentResponse response
	) {
		// 모든 agent 실행 결과는 별도 실행 테이블 없이 validation_logs.details에 보존합니다.
		// 조회/필터링에 필요한 값만 일반 컬럼(agent_type_code, result_code 등)에 복사합니다.
		Map<String, Object> details = baseDetails(response.requestId(), agentType, request, context);
		details.put("output_version", response.outputVersion());
		details.put("agent_status", response.status());
		details.put("response", Map.of(
				"result", response.result() == null ? Map.of() : response.result(),
				"usage", response.usage() == null ? Map.of() : response.usage()
		));
		details.put("error", response.error());

		return agentRepository.saveValidationLog(new ValidationLogCommand(
				projectId,
				usageStatement.getId(),
				requestedItem == null ? null : requestedItem.getId(),
				agentType.validationTypeCode(),
				resultCode(response),
				toJson(details),
				response.usage() == null ? null : response.usage().model(),
				agentType.code(),
				"summary",
				severityCode(response)
		));
	}

	private void saveErrorLog(
			Long projectId,
			UsageStatement usageStatement,
			UsageStatementItem requestedItem,
			AgentType agentType,
			String requestId,
			AgentRunRequest request,
			Map<String, Object> context,
			ApiException exception
	) {
		// FastAPI 호출 실패도 validation_logs에 남깁니다.
		// 다만 실패 로그 저장이 또 실패하면 원래 agent 실패를 가리지 않도록 삼킵니다.
		Map<String, Object> details = baseDetails(requestId, agentType, request, context);
		details.put("agent_status", "error");
		details.put("response", Map.of());
		details.put("error", Map.of(
				"status", exception.getStatus().value(),
				"message", exception.getMessage()
		));

		try {
			agentRepository.saveValidationLog(new ValidationLogCommand(
					projectId,
					usageStatement.getId(),
					requestedItem == null ? null : requestedItem.getId(),
					agentType.validationTypeCode(),
					"error",
					toJson(details),
					null,
					agentType.code(),
					"error",
					"high"
			));
		} catch (RuntimeException ignored) {
			// Preserve the original agent failure when error logging itself fails.
		}
	}

	private Map<String, Object> baseDetails(
			String requestId,
			AgentType agentType,
			AgentRunRequest request,
			Map<String, Object> context
	) {
		Map<String, Object> details = new LinkedHashMap<>();
		details.put("request_id", requestId);
		details.put("input_version", INPUT_VERSION);
		details.put("agent_type", agentType.code());
		details.put("request", Map.of(
				"options", request.optionsOrEmpty(),
				"context_summary", contextSummary(context)
		));
		return details;
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> contextSummary(Map<String, Object> context) {
		Map<String, Object> usageStatement = (Map<String, Object>) context.get("usage_statement");
		List<?> summaries = (List<?>) context.get("summaries");
		List<?> items = (List<?>) context.get("items");
		return Map.of(
				"usage_statement_id", usageStatement.get("id"),
				"summary_count", summaries.size(),
				"item_count", items.size()
		);
	}

	private String resultCode(FastApiAgentResponse response) {
		if (response.error() != null) {
			return "error";
		}
		if (response.result() != null && response.result().get("result_code") instanceof String resultCode) {
			return resultCode;
		}
		return "succeeded".equalsIgnoreCase(response.status()) ? "pass" : response.status();
	}

	private String severityCode(FastApiAgentResponse response) {
		if (response.error() != null) {
			return "high";
		}
		if (response.result() != null && response.result().get("severity_code") instanceof String severityCode) {
			return severityCode;
		}
		return "info";
	}

	private String toJson(Object value) {
		try {
			return objectMapper.writeValueAsString(value);
		} catch (JsonProcessingException exception) {
			throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Agent 실행 로그를 JSON으로 변환할 수 없습니다.");
		}
	}

}
