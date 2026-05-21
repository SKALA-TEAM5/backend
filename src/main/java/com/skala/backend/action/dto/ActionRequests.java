package com.skala.backend.action.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public final class ActionRequests {

	private ActionRequests() {}

	@Schema(description = "조치 요청 생성 요청")
	public record CreateRequest(
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
	public record UpdateStatusRequest(
			@Schema(description = "변경할 상태 코드 (in_progress / closed)", example = "in_progress")
			@NotBlank String statusCode
	) {}
}
