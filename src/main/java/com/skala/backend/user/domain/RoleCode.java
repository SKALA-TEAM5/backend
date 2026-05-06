package com.skala.backend.user.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Arrays;

@Schema(description = "사용자 역할 코드", allowableValues = {"system_admin", "admin", "user", "agent"})
public enum RoleCode {
	SYSTEM_ADMIN("system_admin"),
	ADMIN("admin"),
	USER("user"),
	AGENT("agent");

	private final String value;

	RoleCode(String value) {
		this.value = value;
	}

	@JsonCreator
	public static RoleCode from(String value) {
		return Arrays.stream(values())
				.filter(roleCode -> roleCode.value.equals(value))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException("지원하지 않는 roleCode입니다."));
	}

	@JsonValue
	public String getValue() {
		return value;
	}
}
