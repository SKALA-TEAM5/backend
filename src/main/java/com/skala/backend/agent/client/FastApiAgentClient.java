package com.skala.backend.agent.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

@Component
public class FastApiAgentClient {

	private final RestClient restClient;

	public FastApiAgentClient(
			RestClient.Builder builder,
			@Value("${app.fastapi.base-url:http://localhost:8001}") String baseUrl,
			@Value("${app.fastapi.timeout-seconds:40}") int timeoutSeconds
	) {
		SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
		factory.setConnectTimeout(Duration.ofSeconds(10));
		factory.setReadTimeout(Duration.ofSeconds(timeoutSeconds));

		this.restClient = builder
				.baseUrl(baseUrl)
				.requestFactory(factory)
				.build();
	}

	public void parseUsageStatement(Long fileId) {
		restClient.post()
				.uri("/api/v1/orchestrator/usage-statements/parse")
				.body(Map.of("file_id", fileId))
				.retrieve()
				.toBodilessEntity();
	}

	public void classifyItem(Long projectId, Long usageStatementId, String itemName,
			LocalDate usedOn, String unit, BigDecimal quantity, BigDecimal unitPrice, Long totalAmount) {
		Map<String, Object> body = new HashMap<>();
		body.put("project_id", projectId);
		body.put("usage_statement_id", usageStatementId);
		body.put("item_name", itemName);
		body.put("used_on", usedOn);
		body.put("unit", unit);
		body.put("quantity", quantity);
		body.put("unit_price", unitPrice);
		body.put("total_amount", totalAmount);

		restClient.post()
				.uri("/api/v1/orchestrator/usage-statements/classify")
				.body(body)
				.retrieve()
				.toBodilessEntity();
	}

	public void runValidation(Long projectId, Long usageStatementId) {
		restClient.post()
				.uri("/api/v1/orchestrator/usage-statements/evidence")
				.body(Map.of(
						"project_id", projectId,
						"usage_statement_id", usageStatementId
				))
				.retrieve()
				.toBodilessEntity();
	}
}
