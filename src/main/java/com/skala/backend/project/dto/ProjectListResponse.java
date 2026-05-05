package com.skala.backend.project.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "프로젝트 목록 응답 데이터")
public record ProjectListResponse(
		@Schema(description = "현재 페이지 번호", example = "1")
		int page,
		@Schema(description = "페이지당 항목 수", example = "10")
		int size,
		@Schema(description = "전체 항목 수", example = "42")
		long totalCount,
		@Schema(description = "전체 페이지 수", example = "5")
		int totalPages,
		@Schema(description = "프로젝트 카드 목록")
		List<ProjectCardResponse> items
) {
}
