package com.skala.backend.aiagent.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Arrays;

@Schema(description = "AI Agent 종류 코드", allowableValues = {
		"ocr-agent",
		"classifier-agent",
		"vision-agent",
		"safelee-agent",
		"validator-agent",
		"report-agent"
})
public enum AiAgentTypeCode {
	OCR_AGENT("ocr-agent"),
	CLASSIFIER_AGENT("classifier-agent"),
	VISION_AGENT("vision-agent"),
	SAFELEE_AGENT("safelee-agent"),
	VALIDATOR_AGENT("validator-agent"),
	REPORT_AGENT("report-agent");

	private final String value;

	AiAgentTypeCode(String value) {
		this.value = value;
	}

	@JsonCreator
	public static AiAgentTypeCode from(String value) {
		return Arrays.stream(values())
				.filter(typeCode -> typeCode.value.equals(value))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException("지원하지 않는 agentTypeCode입니다."));
	}

	@JsonValue
	public String getValue() {
		return value;
	}
}
