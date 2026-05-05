package com.skala.backend.project.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.List;

@Schema(description = "프로젝트 담당자 전체 교체 요청")
public record ReplaceProjectAssigneesRequest(
		@Schema(description = "새 담당자 사용자 ID 목록. 빈 배열을 전달하면 담당자를 모두 제거합니다.", example = "[3, 4, 5]")
		@NotNull
		List<Long> assigneeUserIds
) {
}
