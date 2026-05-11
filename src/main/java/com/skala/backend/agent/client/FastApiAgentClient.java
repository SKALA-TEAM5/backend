package com.skala.backend.agent.client;

import com.skala.backend.agent.dto.AgentDtos.AgentType;
import com.skala.backend.agent.dto.AgentDtos.FastApiAgentRequest;
import com.skala.backend.agent.dto.AgentDtos.FastApiAgentResponse;
import com.skala.backend.global.error.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
public class FastApiAgentClient {

	private final RestClient restClient;

	public FastApiAgentClient(
			RestClient.Builder builder,
			@Value("${app.fastapi.base-url:http://localhost:8001}") String baseUrl
	) {
		this.restClient = builder.baseUrl(baseUrl).build();
	}

	public FastApiAgentResponse run(AgentType agentType, FastApiAgentRequest request) {
		try {
			FastApiAgentResponse response = restClient.post()
					.uri("/api/v1/agents/{agentType}/run", agentType.code())
					.body(request)
					.retrieve()
					.body(FastApiAgentResponse.class);
			if (response == null) {
				throw new ApiException(HttpStatus.BAD_GATEWAY, "FastAPI agent 응답이 비어 있습니다.");
			}
			return response;
		} catch (ResourceAccessException exception) {
			throw new ApiException(HttpStatus.GATEWAY_TIMEOUT, "FastAPI agent 호출 시간이 초과되었거나 연결할 수 없습니다.");
		} catch (RestClientResponseException exception) {
			throw new ApiException(HttpStatus.BAD_GATEWAY, "FastAPI agent 호출에 실패했습니다. status=" + exception.getStatusCode().value());
		}
	}
}
