package com.skala.backend.agent.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public final class AgentRequests {

	private AgentRequests() {}

	@Schema(description = "사용내역서 OCR + classi 실행 요청")
	public record ParseRequest(
			@Schema(description = "업로드한 사용내역서 파일 ID", example = "10")
			@NotNull(message = "fileId는 필수입니다.") Long fileId
	) {}

	@Schema(description = "유효성 검증 실행 요청 (link + vision + safety_docs)")
	public record ValidateRequest(
			@Schema(description = "사용내역서 ID", example = "42")
			@NotNull(message = "usageStatementId는 필수입니다.") Long usageStatementId
	) {}

	@Schema(description = "법령 검증 실행 요청")
	public record LegalRequest(
			@Schema(description = "사용내역서 ID", example = "42")
			@NotNull(message = "usageStatementId는 필수입니다.") Long usageStatementId
	) {}

	@Schema(description = "보고서 생성 실행 요청")
	public record ReportRequest(
			@Schema(description = "사용내역서 ID", example = "42")
			@NotNull(message = "usageStatementId는 필수입니다.") Long usageStatementId
	) {}

	@Schema(description = "보고서 details(JSONB) 수정 요청")
	public record UpdateReportRequest(
			@Schema(description = "수정된 보고서 내용 (유효한 JSON 문자열)")
			@NotNull(message = "details는 필수입니다.") String details
	) {}

	@Schema(description = "TODO 확인(체크) 토글 요청")
	public record ConfirmTodoRequest(
			@Schema(description = "true=확인, false=확인 해제", example = "true")
			@NotNull(message = "confirmed는 필수입니다.") Boolean confirmed
	) {}
}
