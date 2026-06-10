package com.skala.backend.chatbot.client;

import com.skala.backend.chatbot.dto.ChatbotRequests.ChatRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.util.HashMap;
import java.util.Map;

@Component
public class ChatbotClient {

	private final WebClient webClient;

	public ChatbotClient(
		WebClient.Builder builder,
		@Value("${app.fastapi.base-url:http://localhost:8001}") String baseUrl
	) {
		this.webClient = builder
			.baseUrl(baseUrl)
			.build();
	}

	/**
	 * FastAPI /chat 에 SSE 요청을 보내고 응답을 Flux<String>으로 반환한다.
	 *
	 * FastAPI가 보내는 SSE 포맷("data: {...}\n\n")을 한 줄씩 수신한다.
	 */
	public Flux<String> stream(ChatRequest request) {
		Map<String, Object> body = new HashMap<>();
		body.put("question", request.question());
		body.put("session_id", request.sessionId());

		return webClient.post()
			.uri("/chat")
			.contentType(MediaType.APPLICATION_JSON)
			.bodyValue(body)
			.retrieve()
			.bodyToFlux(String.class);
	}
}
