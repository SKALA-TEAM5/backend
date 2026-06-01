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
}
