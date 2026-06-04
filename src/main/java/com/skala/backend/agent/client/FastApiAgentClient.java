package com.skala.backend.agent.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.skala.backend.agent.dto.AgentResponses;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class FastApiAgentClient {

	public record ClassifyResult(
			@JsonProperty("item_id") Long itemId,
			@JsonProperty("category_code") String categoryCode
	) {}

	private record ParseResponse(
			@JsonProperty("usage_statement_id") Long usageStatementId,
			@JsonProperty("item_count") int itemCount
	) {}

	private final RestClient restClient;

	public FastApiAgentClient(
			RestClient.Builder builder,
			@Value("${app.fastapi.base-url:http://localhost:8001}") String baseUrl,
			@Value("${app.fastapi.timeout-seconds:60}") int timeoutSeconds
	) {
		SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
		factory.setConnectTimeout(Duration.ofSeconds(10));
		factory.setReadTimeout(Duration.ofSeconds(timeoutSeconds));

		this.restClient = builder
				.baseUrl(baseUrl)
				.requestFactory(factory)
				.build();
	}

	public AgentResponses.ParseResult parseUsageStatement(Long projectId, Long fileId) {
		ParseResponse body = restClient.post()
				.uri("/orchestrator/usage-statements/parse")
				.body(Map.of("project_id", projectId, "file_id", fileId))
				.retrieve()
				.toEntity(ParseResponse.class)
				.getBody();
		return new AgentResponses.ParseResult(body.usageStatementId(), body.itemCount());
	}

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

		return restClient.post()
				.uri("/orchestrator/usage-statements/classify")
				.body(body)
				.retrieve()
				.toEntity(ClassifyResult.class)
				.getBody();
	}

	public List<AgentResponses.AgentRunResult> runValidation(Long projectId, Long usageStatementId, Long triggeredByUserId) {
		return restClient.post()
				.uri("/orchestrator/usage-statements/validate")
				.body(Map.of(
						"project_id",           projectId,
						"usage_statement_id",   usageStatementId,
						"triggered_by_user_id", triggeredByUserId
				))
				.retrieve()
				.toEntity(new ParameterizedTypeReference<List<AgentResponses.AgentRunResult>>() {})
				.getBody();
	}

	public AgentResponses.AgentRunResult runLegal(Long projectId, Long usageStatementId, Long triggeredByUserId) {
		return restClient.post()
				.uri("/orchestrator/usage-statements/legal")
				.body(Map.of(
						"project_id",           projectId,
						"usage_statement_id",   usageStatementId,
						"triggered_by_user_id", triggeredByUserId
				))
				.retrieve()
				.toEntity(AgentResponses.AgentRunResult.class)
				.getBody();
	}

	public AgentResponses.AgentRunResult runReport(Long projectId, Long usageStatementId, Long triggeredByUserId) {
		Map<String, Object> raw = restClient.post()
				.uri("/orchestrator/usage-statements/report")
				.body(Map.of(
						"project_id",           projectId,
						"usage_statement_id",   usageStatementId,
						"triggered_by_user_id", triggeredByUserId
				))
				.retrieve()
				.toEntity(new ParameterizedTypeReference<Map<String, Object>>() {})
				.getBody();
		return mapReportResponse(raw);
	}

	static AgentResponses.AgentRunResult mapReportResponse(Map<String, Object> raw) {
		String status  = (String) raw.get("status");
		String message = (String) raw.get("message");

		if (!"success".equals(status)) {
			return new AgentResponses.AgentRunResult("report", status != null ? status : "fail", "fail", message, null);
		}

		@SuppressWarnings("unchecked") Map<String, Object> result     = (Map<String, Object>) raw.get("result");
		@SuppressWarnings("unchecked") Map<String, Object> report     = (Map<String, Object>) result.get("report");
		@SuppressWarnings("unchecked") Map<String, Object> reportDraft = (Map<String, Object>) result.get("reportDraft");

		return new AgentResponses.AgentRunResult(
				"report",
				(String) report.get("status_code"),
				(String) report.get("result_code"),
				(String) report.get("reason"),
				reportDraft
		);
	}
}
