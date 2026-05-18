package com.skala.backend.agent.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

// FastAPI HTTP 클라이언트 스켈레톤 — 엔드포인트 계약 확정 후 메서드 추가 예정
@Component
public class FastApiAgentClient {

	private final RestClient restClient;

	public FastApiAgentClient(
			RestClient.Builder builder,
			@Value("${app.fastapi.base-url:http://localhost:8001}") String baseUrl
	) {
		this.restClient = builder.baseUrl(baseUrl).build();
	}
}
