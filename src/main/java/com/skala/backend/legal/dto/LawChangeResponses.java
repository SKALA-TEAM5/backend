package com.skala.backend.legal.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

public class LawChangeResponses {

    @Schema(name = "LawChangeRecentResponse")
    public record RecentResponse(
            @Schema(description = "변경 감지 여부")
            boolean hasChanges,
            @Schema(description = "마지막 배치 실행 시각")
            Instant lastRunAt,
            @Schema(description = "변경된 법령 목록")
            List<ChangedLaw> changedLaws
    ) {}

    @Schema(name = "LawChangeChangedLaw")
    public record ChangedLaw(
            @Schema(description = "법령명")
            String lawName,
            @Schema(description = "조")
            String articleNo,
            @Schema(description = "항")
            String paragraphNo,
            @Schema(description = "호")
            String itemNo,
            @Schema(description = "변경 유형 (added / updated / deleted)")
            String changeType
    ) {}
}
