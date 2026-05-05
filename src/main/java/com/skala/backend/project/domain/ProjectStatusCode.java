package com.skala.backend.project.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Arrays;

@Schema(description = "프로젝트 상태 코드", allowableValues = {"active", "completed", "suspended"})
public enum ProjectStatusCode {
	ACTIVE("active"),
	COMPLETED("completed"),
	SUSPENDED("suspended");

	private final String value;

	ProjectStatusCode(String value) {
		this.value = value;
	}

	@JsonCreator
	public static ProjectStatusCode from(String value) {
		return Arrays.stream(values())
				.filter(status -> status.value.equals(value))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException("지원하지 않는 status입니다."));
	}

	@JsonValue
	public String getValue() {
		return value;
	}
}
