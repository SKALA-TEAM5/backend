package com.skala.backend.aiagent.client;

import com.skala.backend.aiagent.client.AiAgentClientDtos.AiAgentClientRequest;
import com.skala.backend.aiagent.client.AiAgentClientDtos.AiAgentClientResponse;
import com.skala.backend.aiagent.config.AiAgentProperties;
import com.skala.backend.global.error.ApiException;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
@Profile("!mock-aiagent")
public class FastApiAiAgentClient implements AiAgentClient {

	private final RestClient restClient;
	private final AiAgentProperties properties;

	public FastApiAiAgentClient(AiAgentProperties properties, RestClient.Builder restClientBuilder) {
		if (properties.getBaseUrl() == null || properties.getBaseUrl().isBlank()) {
			throw new IllegalStateException("app.ai-agent.base-url 설정이 필요합니다.");
		}
		SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
		requestFactory.setConnectTimeout(properties.getConnectTimeout());
		requestFactory.setReadTimeout(properties.getReadTimeout());
		this.restClient = restClientBuilder
				.baseUrl(properties.getBaseUrl())
				.requestFactory(requestFactory)
				.build();
		this.properties = properties;
	}

	@Override
	public AiAgentClientResponse run(AiAgentClientRequest request) {
		try {
			AiAgentClientResponse response = restClient.post()
					.uri(properties.getRunPath())
					.body(request)
					.retrieve()
					.body(AiAgentClientResponse.class);
			if (response == null) {
				throw new ApiException(HttpStatus.BAD_GATEWAY, "AI Agent 응답이 비어 있습니다.");
			}
			if (!request.aiAgentRunId().equals(response.aiAgentRunId())) {
				throw new ApiException(HttpStatus.BAD_GATEWAY, "AI Agent 응답의 run id가 요청과 다릅니다.");
			}
			return response;
		} catch (ApiException exception) {
			throw exception;
		} catch (RestClientException exception) {
			throw new ApiException(HttpStatus.BAD_GATEWAY, "AI Agent 호출에 실패했습니다.");
		}
	}
}
