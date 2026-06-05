package com.skala.backend.chatbot.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class ChatbotRequests {

	public record ChatRequest(
		@NotBlank(message = "질문을 입력해 주세요.")
		@Size(min = 1, max = 500, message = "질문은 1자 이상 500자 이하로 입력해 주세요.")
		String question,

		String sessionId
	) {}
}
