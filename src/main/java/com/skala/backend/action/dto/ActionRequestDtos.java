package com.skala.backend.action.dto;

import com.skala.backend.action.domain.ActionRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.time.LocalDate;

public class ActionRequestDtos {

	@Schema(description = "조치 요청 생성 요청")
	public record CreateActionRequestRequest(
			@Schema(description = "요청 제목", example = "안전시설 보완 설치 요청")
			@NotBlank String title,
			@Schema(description = "요청 사유", example = "현장사진 검토 결과 안전망 미설치 확인")
			String reason,
			@Schema(description = "담당자 사용자 ID", example = "5")
			@NotNull Long assigneeUserId,
			@Schema(description = "관련 사용내역서 ID", example = "10")
			Long usageStatementId,
			@Schema(description = "관련 세부항목 ID", example = "42")
			Long usageStatementItemId,
			@Schema(description = "처리 기한", example = "2026-06-30")
			LocalDate dueDate
	) {}

	@Schema(description = "조치 요청 상태 업데이트 요청")
	public record UpdateActionRequestStatusRequest(
			@Schema(description = "변경할 상태 코드 (in_progress / resolved / closed)", example = "in_progress")
			@NotBlank String statusCode
	) {}

	@Schema(description = "조치 요청 상세 정보")
	public record ActionRequestResponse(
			@Schema(description = "조치 요청 ID", example = "1")
			Long id,
			@Schema(description = "프로젝트 ID", example = "1")
			Long projectId,
			@Schema(description = "관련 사용내역서 ID", example = "10")
			Long usageStatementId,
			@Schema(description = "관련 세부항목 ID", example = "42")
			Long usageStatementItemId,
			@Schema(description = "요청자 사용자 ID", example = "2")
			Long requestedByUserId,
			@Schema(description = "담당자 사용자 ID", example = "5")
			Long assigneeUserId,
			@Schema(description = "요청 제목", example = "안전시설 보완 설치 요청")
			String title,
			@Schema(description = "요청 사유", example = "현장사진 검토 결과 안전망 미설치 확인")
			String reason,
			@Schema(description = "상태 코드", example = "open")
			String statusCode,
			@Schema(description = "처리 기한", example = "2026-06-30")
			LocalDate dueDate,
			@Schema(description = "생성 일시")
			Instant createdAt,
			@Schema(description = "처리 완료 일시")
			Instant resolvedAt
	) {
		public static ActionRequestResponse from(ActionRequest ar) {
			return new ActionRequestResponse(
					ar.getId(),
					ar.getProjectId(),
					ar.getUsageStatementId(),
					ar.getUsageStatementItemId(),
					ar.getRequestedByUserId(),
					ar.getAssigneeUserId(),
					ar.getTitle(),
					ar.getReason(),
					ar.getStatusCode(),
					ar.getDueDate(),
					ar.getCreatedAt(),
					ar.getResolvedAt()
			);
		}
	}
}
