package com.skala.backend.usage.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

public enum UsageStatementStatus {
    DRAFT("draft"),
    UPLOAD_COMPLETED("upload_completed"),
    SUPPLEMENT_REQUIRED("supplement_required"),
    REVIEW_COMPLETED("review_completed");

    private final String code;

    UsageStatementStatus(String code) {
        this.code = code;
    }

    @JsonCreator
    public static UsageStatementStatus from(String code) {
        return Arrays.stream(values())
                .filter(s -> s.code.equals(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("지원하지 않는 사용내역서 상태 코드입니다: " + code));
    }

    @JsonValue
    public String getCode() {
        return code;
    }
}
