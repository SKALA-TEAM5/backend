package com.skala.backend.agent.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skala.backend.agent.dto.AgentResponses;
import com.skala.backend.global.error.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

@Component
public class FastApiAgentClient {

	public record ClassifyResult(
			boolean categoryChanged,
			List<CategoryChangeEntry> changes
	) {
		public record CategoryChangeEntry(
				String itemName,
				String fromCategoryCode,
				String fromCategoryName,
				String toCategoryCode,
				String toCategoryName
		) {}
	}

	private final RestClient restClient;
	private final ObjectMapper objectMapper = new ObjectMapper();

	public FastApiAgentClient(
			RestClient.Builder builder,
			@Value("${app.fastapi.base-url:http://localhost:8001}") String baseUrl
	) {
		SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
		factory.setConnectTimeout(Duration.ofSeconds(10));
		factory.setReadTimeout(Duration.ofMinutes(40));

		this.restClient = builder
				.baseUrl(baseUrl)
				.requestFactory(factory)
				.build();
	}

	private <T> T callFastApi(Supplier<T> call) {
		try {
			return call.get();
		} catch (RestClientResponseException e) {
			String message = extractMessage(e.getResponseBodyAsString());
			if (e.getStatusCode().is4xxClientError()) {
				throw new ApiException(HttpStatus.valueOf(e.getStatusCode().value()), message);
			}
			throw new ApiException(HttpStatus.BAD_GATEWAY, "AI 서비스 처리 중 오류가 발생했습니다: " + message);
		} catch (ResourceAccessException e) {
			throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "AI 서비스에 연결할 수 없습니다.");
		}
	}

	private String extractMessage(String body) {
		if (body == null || body.isBlank()) return "AI 서비스 오류";
		try {
			JsonNode root = objectMapper.readTree(body);
			JsonNode detail = root.get("detail");
			if (detail != null) {
				if (detail.isTextual()) return detail.asText();
				if (detail.isArray() && !detail.isEmpty()) return detail.get(0).path("msg").asText("AI 서비스 오류");
			}
			JsonNode message = root.get("message");
			if (message != null && message.isTextual()) return message.asText();
		} catch (Exception ignored) {}
		return body.length() > 200 ? body.substring(0, 200) : body;
	}

	// parse: usage_statement_id → top-level, item_count → result.item_count
	public AgentResponses.ParseResult parseUsageStatement(Long projectId, Long fileId) {
		Map<String, Object> raw = callFastApi(() -> restClient.post()
				.uri("/orchestrator/usage-statements/parse")
				.body(Map.of("project_id", projectId, "file_id", fileId))
				.retrieve()
				.toEntity(new ParameterizedTypeReference<Map<String, Object>>() {})
				.getBody());

		Long usageStatementId = toLong(raw.get("usage_statement_id"));

		@SuppressWarnings("unchecked")
		Map<String, Object> result = (Map<String, Object>) raw.get("result");
		int itemCount = result != null ? toInt(result.get("item_count")) : 0;

		return new AgentResponses.ParseResult(usageStatementId, itemCount);
	}

	// classify: result.payload.changes[0] (변경) 또는 result.payload.results[0] (유지) 에서 추출
	public ClassifyResult classifyItem(Long projectId, Long usageStatementId, String categoryCode,
			String itemName, LocalDate usedOn, String unit, BigDecimal quantity, BigDecimal unitPrice, BigDecimal totalAmount) {
		Map<String, Object> body = new HashMap<>();
		body.put("project_id", projectId);
		body.put("usage_statement_id", usageStatementId);
		body.put("category_code", categoryCode);
		body.put("item_name", itemName);
		body.put("used_on", usedOn);
		body.put("unit", unit);
		body.put("quantity", quantity);
		body.put("unit_price", unitPrice);
		body.put("total_amount", totalAmount);

		Map<String, Object> raw = callFastApi(() -> restClient.post()
				.uri("/orchestrator/usage-statements/classify")
				.body(body)
				.retrieve()
				.toEntity(new ParameterizedTypeReference<Map<String, Object>>() {})
				.getBody());

		return extractClassifyResult(raw);
	}

	// validate: result.{"safety-doc", "link", "vision"} 각각 AgentRunResult로 변환
	public List<AgentResponses.AgentRunResult> runValidation(Long projectId, Long usageStatementId, Long triggeredByUserId) {
		Map<String, Object> raw = callFastApi(() -> restClient.post()
				.uri("/orchestrator/usage-statements/validate")
				.body(Map.of(
						"project_id",           projectId,
						"usage_statement_id",   usageStatementId,
						"triggered_by_user_id", triggeredByUserId
				))
				.retrieve()
				.toEntity(new ParameterizedTypeReference<Map<String, Object>>() {})
				.getBody());

		@SuppressWarnings("unchecked")
		Map<String, Object> result = (Map<String, Object>) raw.get("result");
		if (result == null) return List.of();

		return List.of("safety-doc", "link", "vision").stream()
				.filter(result::containsKey)
				.map(agentType -> {
					@SuppressWarnings("unchecked")
					Map<String, Object> agentResult = (Map<String, Object>) result.get(agentType);
					return toAgentRunResult(agentType, agentResult);
				})
				.toList();
	}

	// legal: result.legal 에서 AgentRunResult 추출
	public AgentResponses.AgentRunResult runLegal(Long projectId, Long usageStatementId, Long triggeredByUserId) {
		Map<String, Object> raw = callFastApi(() -> restClient.post()
				.uri("/orchestrator/usage-statements/legal")
				.body(Map.of(
						"project_id",           projectId,
						"usage_statement_id",   usageStatementId,
						"triggered_by_user_id", triggeredByUserId
				))
				.retrieve()
				.toEntity(new ParameterizedTypeReference<Map<String, Object>>() {})
				.getBody());

		String status  = (String) raw.get("status");
		String message = (String) raw.get("message");

		if (!"success".equals(status)) {
			return new AgentResponses.AgentRunResult("legal", status != null ? status : "fail", "fail", message, null);
		}

		@SuppressWarnings("unchecked")
		Map<String, Object> result = (Map<String, Object>) raw.get("result");
		@SuppressWarnings("unchecked")
		Map<String, Object> legal  = result != null ? (Map<String, Object>) result.get("legal") : null;

		return toAgentRunResult("legal", legal);
	}

	// report: result.report + result.report.result.reportDraft 에서 추출
	public AgentResponses.AgentRunResult runReport(Long projectId, Long usageStatementId, Long triggeredByUserId) {
		Map<String, Object> raw = callFastApi(() -> restClient.post()
				.uri("/orchestrator/usage-statements/report")
				.body(Map.of(
						"project_id",           projectId,
						"usage_statement_id",   usageStatementId,
						"triggered_by_user_id", triggeredByUserId
				))
				.retrieve()
				.toEntity(new ParameterizedTypeReference<Map<String, Object>>() {})
				.getBody());
		return mapReportResponse(raw);
	}

	static AgentResponses.AgentRunResult mapReportResponse(Map<String, Object> raw) {
		String status  = (String) raw.get("status");
		String message = (String) raw.get("message");

		if (!"success".equals(status)) {
			return new AgentResponses.AgentRunResult("report", status != null ? status : "fail", "fail", message, null);
		}

		@SuppressWarnings("unchecked")
		Map<String, Object> result = (Map<String, Object>) raw.get("result");
		@SuppressWarnings("unchecked")
		Map<String, Object> report = result != null ? (Map<String, Object>) result.get("report") : null;

		if (report == null) {
			return new AgentResponses.AgentRunResult("report", "fail", "fail", message, null);
		}

		// reportDraft: result.reportDraft (report 형제 위치)
		@SuppressWarnings("unchecked")
		Map<String, Object> reportDraft = result != null ? (Map<String, Object>) result.get("reportDraft") : null;

		return new AgentResponses.AgentRunResult(
				"report",
				(String) report.get("status_code"),
				(String) report.get("result_code"),
				(String) report.get("reason"),
				reportDraft
		);
	}

	private ClassifyResult extractClassifyResult(Map<String, Object> raw) {
		if (raw == null) return new ClassifyResult(false, List.of());

		@SuppressWarnings("unchecked")
		Map<String, Object> result = (Map<String, Object>) raw.get("result");
		@SuppressWarnings("unchecked")
		Map<String, Object> payload = result != null ? (Map<String, Object>) result.get("payload") : null;

		@SuppressWarnings("unchecked")
		List<Map<String, Object>> results = payload != null ? (List<Map<String, Object>>) payload.get("results") : null;
		if (results != null && !results.isEmpty()) {
			String reason = (String) results.get(0).get("reason");
			if (reason != null && reason.contains("llm(unclassified)")) {
				throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
						"해당 항목은 산업안전보건관리비 9개 항목으로 분류하기 어렵습니다. 항목명을 다시 확인해 주세요.");
			}
		}

		@SuppressWarnings("unchecked")
		List<Map<String, Object>> changes = payload != null ? (List<Map<String, Object>>) payload.get("changes") : null;
		if (changes == null || changes.isEmpty()) return new ClassifyResult(false, List.of());

		List<ClassifyResult.CategoryChangeEntry> entries = changes.stream()
				.map(c -> new ClassifyResult.CategoryChangeEntry(
						(String) c.get("item_name"),
						(String) c.get("from_category_code"),
						(String) c.get("from_category_name"),
						(String) c.get("to_category_code"),
						(String) c.get("to_category_name")
				))
				.toList();

		return new ClassifyResult(true, entries);
	}

	private AgentResponses.AgentRunResult toAgentRunResult(String agentType, Map<String, Object> agentResult) {
		if (agentResult == null) {
			return new AgentResponses.AgentRunResult(agentType, "fail", "fail", null, null);
		}
		return new AgentResponses.AgentRunResult(
				agentType,
				(String) agentResult.get("status_code"),
				(String) agentResult.get("result_code"),
				(String) agentResult.get("reason"),
				null
		);
	}

	private static Long toLong(Object value) {
		if (value == null) return null;
		if (value instanceof Number number) return number.longValue();
		try { return Long.parseLong(value.toString()); } catch (NumberFormatException e) { return null; }
	}

	private static int toInt(Object value) {
		if (value == null) return 0;
		if (value instanceof Number number) return number.intValue();
		try { return Integer.parseInt(value.toString()); } catch (NumberFormatException e) { return 0; }
	}
}
