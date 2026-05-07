package com.skala.backend.project.dto;

import com.skala.backend.project.domain.Project;
import com.skala.backend.project.domain.ProjectStatusCode;
import com.skala.backend.project.repository.ProjectCardRow;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Schema(description = "프로젝트 목록 카드 정보")
public record ProjectCardResponse(
		@Schema(description = "프로젝트 ID", example = "1")
		Long id,
		@Schema(description = "프로젝트명", example = "스마트 안전관리 시스템 구축")
		String projectName,
		@Schema(description = "담당자 이름 목록", example = "[\"김스칼라\", \"이현장\"]")
		List<String> assigneeNames,
		@Schema(description = "담당자 수", example = "2")
		long assigneeCount,
		@Schema(description = "계약번호", example = "CN-2026-001")
		String contractNo,
		@Schema(description = "공사 시작일", example = "2026-01-01")
		LocalDate constructionStartDate,
		@Schema(description = "공사 종료일", example = "2026-12-31")
		LocalDate constructionEndDate,
		@Schema(description = "최근 누적 공정률", example = "35.5")
		BigDecimal latestCumulativeProgressRate,
		@Schema(description = "프로젝트 상태", example = "active")
		ProjectStatusCode status,
		@Schema(description = "처리 요청 존재 여부", example = "false")
		boolean hasActionRequest,
		@Schema(description = "관리자가 아직 확인하지 않은 매칭 파일 수", example = "3")
		long uncheckedMatchedFileCount
) {

	public static ProjectCardResponse from(ProjectCardRow row, boolean includeUncheckedMatchedFileCount) {
		return new ProjectCardResponse(
				row.id(),
				row.projectName(),
				row.assigneeNames(),
				row.assigneeCount(),
				row.contractNo(),
				row.constructionStartDate(),
				row.constructionEndDate(),
				row.latestCumulativeProgressRate(),
				row.status(),
				row.hasActionRequest(),
				includeUncheckedMatchedFileCount ? row.uncheckedMatchedFileCount() : 0
		);
	}

	public static ProjectCardResponse of(
			Project project,
			long assigneeCount,
			BigDecimal latestCumulativeProgressRate,
			boolean hasActionRequest,
			long uncheckedMatchedFileCount
	) {
		return new ProjectCardResponse(
				project.getId(),
				project.getProjectName(),
				List.of(),
				assigneeCount,
				project.getContractNo(),
				project.getConstructionStartDate(),
				project.getConstructionEndDate(),
				latestCumulativeProgressRate,
				project.getStatus(),
				hasActionRequest,
				uncheckedMatchedFileCount
		);
	}
}
