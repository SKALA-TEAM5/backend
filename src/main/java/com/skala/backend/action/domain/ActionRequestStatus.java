package com.skala.backend.action.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

public enum ActionRequestStatus {
    OPEN("open"),
    IN_PROGRESS("in_progress"),
    CLOSED("closed");

    private final String code;

    ActionRequestStatus(String code) {
        this.code = code;
    }

    @JsonCreator
    public static ActionRequestStatus from(String code) {
        return Arrays.stream(values())
                .filter(s -> s.code.equals(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("지원하지 않는 조치 요청 상태 코드입니다: " + code));
    }

    @JsonValue
    public String getCode() {
        return code;
    }
}
