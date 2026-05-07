package com.skala.backend.aiagent.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Arrays;

@Schema(description = "AI Agent 실행 상태 코드", allowableValues = {
		"requested",
		"running",
		"completed",
		"failed",
		"cancelled"
})
public enum AiAgentRunStatusCode {
	REQUESTED("requested"),
	RUNNING("running"),
	COMPLETED("completed"),
	FAILED("failed"),
	CANCELLED("cancelled");

	private final String value;

	AiAgentRunStatusCode(String value) {
		this.value = value;
	}

	@JsonCreator
	public static AiAgentRunStatusCode from(String value) {
		return Arrays.stream(values())
				.filter(statusCode -> statusCode.value.equals(value))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException("지원하지 않는 AI Agent 실행 상태입니다."));
	}

	@JsonValue
	public String getValue() {
		return value;
	}
}
