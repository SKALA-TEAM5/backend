package com.skala.backend.project.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "프로젝트 담당자 목록 응답 데이터")
public record ProjectAssigneeListResponse(
		@Schema(description = "프로젝트 ID", example = "1")
		Long projectId,
		@Schema(description = "프로젝트 담당자 목록")
		List<ProjectAssigneeResponse> assignees
) {
}
