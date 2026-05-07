package com.skala.backend.project.dto;

import com.skala.backend.project.domain.Project;
import com.skala.backend.project.domain.ProjectStatusCode;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Schema(description = "프로젝트 상세 정보")
public record ProjectDetailResponse(
		@Schema(description = "프로젝트 ID", example = "1")
		Long id,
		@Schema(description = "계약번호", example = "CN-2026-001")
		String contractNo,
		@Schema(description = "시공사명", example = "스칼라건설")
		String constructionCompany,
		@Schema(description = "프로젝트명", example = "스마트 안전관리 시스템 구축")
		String projectName,
		@Schema(description = "현장 위치", example = "서울특별시 강남구 테헤란로 123")
		String siteLocation,
		@Schema(description = "대표자명", example = "홍길동")
		String representativeName,
		@Schema(description = "계약금액", example = "1200000000")
		BigDecimal contractAmount,
		@Schema(description = "공사 시작일", example = "2026-01-01")
		LocalDate constructionStartDate,
		@Schema(description = "공사 종료일", example = "2026-12-31")
		LocalDate constructionEndDate,
		@Schema(description = "발주처명", example = "스칼라시")
		String clientName,
		@Schema(description = "책정 예산", example = "1500000000")
		BigDecimal appropriatedAmount,
		@Schema(description = "프로젝트 상태", example = "active")
		ProjectStatusCode status,
		@Schema(description = "프로젝트 담당자 목록")
		List<ProjectAssigneeResponse> assignees,
		@Schema(description = "관리자가 아직 확인하지 않은 매칭 파일 수", example = "3")
		long uncheckedMatchedFileCount,
		@Schema(description = "생성 일시", example = "2026-05-05T01:00:00Z")
		Instant createdAt,
		@Schema(description = "수정 일시", example = "2026-05-05T01:00:00Z")
		Instant updatedAt
) {

	public static ProjectDetailResponse of(Project project, List<ProjectAssigneeResponse> assignees, long uncheckedMatchedFileCount) {
		return new ProjectDetailResponse(
				project.getId(),
				project.getContractNo(),
				project.getConstructionCompany(),
				project.getProjectName(),
				project.getSiteLocation(),
				project.getRepresentativeName(),
				project.getContractAmount(),
				project.getConstructionStartDate(),
				project.getConstructionEndDate(),
				project.getClientName(),
				project.getAppropriatedAmount(),
				project.getStatus(),
				assignees,
				uncheckedMatchedFileCount,
				project.getCreatedAt(),
				project.getUpdatedAt()
		);
	}
}
