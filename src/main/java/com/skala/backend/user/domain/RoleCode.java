package com.skala.backend.user.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

public enum RoleCode {
	ADMIN("admin"),
	HQ("hq"),
	SITE("site"),
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
