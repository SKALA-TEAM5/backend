package com.skala.backend.project.dto;

import com.skala.backend.project.domain.Project;
import com.skala.backend.project.domain.ProjectStatusCode;
import com.skala.backend.project.domain.ProjectUserAssignment;
import com.skala.backend.project.repository.ProjectCardRow;
import com.skala.backend.user.domain.RoleCode;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class ProjectResponses {

	private ProjectResponses() {}

	@Schema(description = "프로젝트 담당자 정보")
	public record AssigneeResponse(
			@Schema(description = "담당자 사용자 ID", example = "3") Long userId,
			@Schema(description = "담당자 사번", example = "EMP003") String employeeNo,
			@Schema(description = "담당자 이름", example = "이현장") String realName,
			@Schema(description = "담당자 역할 코드", example = "user") RoleCode roleCode,
			@Schema(description = "담당자 배정 일시", example = "2026-05-05T01:00:00Z") Instant assignedAt,
			@Schema(description = "담당자를 배정한 사용자 ID", example = "1") Long assignedByUserId
	) {
		public static AssigneeResponse from(ProjectUserAssignment assignment) {
			return new AssigneeResponse(
					assignment.getUser().getId(),
					assignment.getUser().getEmployeeNo(),
					assignment.getUser().getRealName(),
					assignment.getUser().getRoleCode(),
					assignment.getCreatedAt(),
					assignment.getAssignedBy() == null ? null : assignment.getAssignedBy().getId()
			);
		}
	}

	@Schema(description = "프로젝트 담당자 목록 응답 데이터")
	public record AssigneeListResponse(
			@Schema(description = "프로젝트 ID", example = "1") Long projectId,
			@Schema(description = "프로젝트 담당자 목록") List<AssigneeResponse> assignees
	) {}

	@Schema(description = "프로젝트 목록 카드 정보")
	public record CardResponse(
			@Schema(description = "프로젝트 ID", example = "1") Long id,
			@Schema(description = "프로젝트명", example = "스마트 안전관리 시스템 구축") String projectName,
			@Schema(description = "담당자 이름 목록", example = "[\"김스칼라\", \"이현장\"]") List<String> assigneeNames,
			@Schema(description = "담당자 수", example = "2") long assigneeCount,
			@Schema(description = "계약번호", example = "CN-2026-001") String contractNo,
			@Schema(description = "공사 시작일", example = "2026-01-01") LocalDate constructionStartDate,
			@Schema(description = "공사 종료일", example = "2026-12-31") LocalDate constructionEndDate,
			@Schema(description = "최근 누적 공정률", example = "35.5") BigDecimal latestCumulativeProgressRate,
			@Schema(description = "프로젝트 상태", example = "active") ProjectStatusCode status,
			@Schema(description = "관리자가 아직 확인하지 않은 매칭 파일 수", example = "3") long uncheckedMatchedFileCount,
			@Schema(description = "최신 사용내역서 상태", example = "upload_completed") String latestUsageStatementStatusCode
	) {
		public static CardResponse from(ProjectCardRow row, boolean includeUncheckedMatchedFileCount) {
			return new CardResponse(
					row.id(), row.projectName(), row.assigneeNames(), row.assigneeCount(),
					row.contractNo(), row.constructionStartDate(), row.constructionEndDate(),
					row.latestCumulativeProgressRate(), row.status(),
					includeUncheckedMatchedFileCount ? row.uncheckedMatchedFileCount() : 0,
					row.latestUsageStatementStatusCode()
			);
		}
	}

	@Schema(description = "프로젝트 목록 응답 데이터")
	public record ListResponse(
			@Schema(description = "현재 페이지 번호", example = "1") int page,
			@Schema(description = "페이지당 항목 수", example = "10") int size,
			@Schema(description = "전체 항목 수", example = "42") long totalCount,
			@Schema(description = "전체 페이지 수", example = "5") int totalPages,
			@Schema(description = "프로젝트 카드 목록") List<CardResponse> items
	) {}

	@Schema(description = "프로젝트 상세 정보")
	public record DetailResponse(
			@Schema(description = "프로젝트 ID", example = "1") Long id,
			@Schema(description = "계약번호", example = "CN-2026-001") String contractNo,
			@Schema(description = "시공사명", example = "스칼라건설") String constructionCompany,
			@Schema(description = "프로젝트명", example = "스마트 안전관리 시스템 구축") String projectName,
			@Schema(description = "현장 위치", example = "서울특별시 강남구 테헤란로 123") String siteLocation,
			@Schema(description = "대표자명", example = "홍길동") String representativeName,
			@Schema(description = "계약금액", example = "1200000000") BigDecimal contractAmount,
			@Schema(description = "공사 시작일", example = "2026-01-01") LocalDate constructionStartDate,
			@Schema(description = "공사 종료일", example = "2026-12-31") LocalDate constructionEndDate,
			@Schema(description = "발주처명", example = "스칼라시") String clientName,
			@Schema(description = "책정 예산", example = "1500000000") BigDecimal appropriatedAmount,
			@Schema(description = "프로젝트 상태", example = "active") ProjectStatusCode status,
			@Schema(description = "프로젝트 담당자 목록") List<AssigneeResponse> assignees,
			@Schema(description = "관리자가 아직 확인하지 않은 매칭 파일 수", example = "3") long uncheckedMatchedFileCount,
			@Schema(description = "생성 일시", example = "2026-05-05T01:00:00Z") Instant createdAt,
			@Schema(description = "수정 일시", example = "2026-05-05T01:00:00Z") Instant updatedAt
	) {
		public static DetailResponse of(Project project, List<AssigneeResponse> assignees, long uncheckedMatchedFileCount) {
			return new DetailResponse(
					project.getId(), project.getContractNo(), project.getConstructionCompany(),
					project.getProjectName(), project.getSiteLocation(), project.getRepresentativeName(),
					project.getContractAmount(), project.getConstructionStartDate(), project.getConstructionEndDate(),
					project.getClientName(), project.getAppropriatedAmount(), project.getStatus(),
					assignees, uncheckedMatchedFileCount, project.getCreatedAt(), project.getUpdatedAt()
			);
		}
	}

	@Schema(description = "프로젝트 상세 응답 데이터")
	public record DetailDataResponse(
			@Schema(description = "프로젝트 상세 정보") DetailResponse project
	) {}
}
