package com.skala.backend.agent.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

public enum AgentLogStatus {
    PENDING("pending"),
    RUNNING("running"),
    SUCCESS("success"),
    FAIL("fail"),
    CANCELED("canceled");

    private final String code;

    AgentLogStatus(String code) {
        this.code = code;
    }

    @JsonCreator
    public static AgentLogStatus from(String code) {
        return Arrays.stream(values())
                .filter(s -> s.code.equals(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("지원하지 않는 에이전트 로그 상태 코드입니다: " + code));
    }

    @JsonValue
    public String getCode() {
        return code;
    }
}
