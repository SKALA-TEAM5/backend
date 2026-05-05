package com.skala.backend.project.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "프로젝트 상세 응답 데이터")
public record ProjectDetailDataResponse(
		@Schema(description = "프로젝트 상세 정보")
		ProjectDetailResponse project
) {
}
