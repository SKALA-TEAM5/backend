package com.skala.backend.agent.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

public enum AgentTypeCode {
    CLASSI("classi"),
    SAFETY_DOC("safety-doc"),
    LINK("link"),
    VISION("vision"),
    LEGAL("legal"),
    REPORT("report"),
    ORCHESTRATOR("orchestrator");

    private final String code;

    AgentTypeCode(String code) {
        this.code = code;
    }

    @JsonCreator
    public static AgentTypeCode from(String code) {
        return Arrays.stream(values())
                .filter(t -> t.code.equals(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("지원하지 않는 에이전트 유형 코드입니다: " + code));
    }

    @JsonValue
    public String getCode() {
        return code;
    }
}
