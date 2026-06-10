package com.skala.backend.chatbot.controller;

import com.skala.backend.auth.security.AuthenticatedUser;
import com.skala.backend.chatbot.client.ChatbotClient;
import com.skala.backend.chatbot.dto.ChatbotRequests.ChatRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@RestController
@RequestMapping("/chat")
@RequiredArgsConstructor
public class ChatbotController {

	private final ChatbotClient chatbotClient;

	// SseEmitter 전송은 별도 스레드에서 실행 (MVC 스레드 블로킹 방지)
	private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

	/**
	 * POST /chat
	 *
	 * FastAPI /api/v1/chat SSE를 구독하여 프론트엔드로 중계한다.
	 * JWT 인증은 JwtAuthenticationFilter가 자동 처리한다.
	 *
	 * SSE 이벤트 흐름 (FastAPI → Spring → Frontend):
	 *   data: {"type": "session_id", "value": "..."}
	 *   data: {"type": "intent",     "value": "카테고리판단"}
	 *   data: {"type": "token",      "value": "CAT_03"}
	 *   data: {"type": "sources",    "value": [...]}
	 *   data: {"type": "error",    "value": [...]}
	 *   data: [DONE]
	 */
	@PostMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public SseEmitter chat(
			@Valid @RequestBody ChatRequest request,
			@AuthenticationPrincipal AuthenticatedUser currentUser
	) {
		log.info("[ChatbotController] userId={} | question={}", currentUser.id(), request.question());

		SseEmitter emitter = new SseEmitter(300_000L); // 타임아웃 5분

		executor.submit(() ->
				chatbotClient.stream(request)
						.subscribe(
								line -> {
									try {
										// FastAPI SSE 포맷("data: {...}\n\n")을 그대로 프론트에 전달
										emitter.send(line);
									} catch (IOException e) {
										log.warn("[ChatbotController] 전송 실패 (클라이언트 연결 끊김): {}", e.getMessage());
										emitter.complete();
									}
								},
								error -> {
									log.error("[ChatbotController] FastAPI 스트리밍 오류: {}", error.getMessage());
									emitter.complete();
								},
								emitter::complete
						)
		);

		return emitter;
	}
}
