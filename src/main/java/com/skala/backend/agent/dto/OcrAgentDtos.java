package com.skala.backend.agent.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Map;

public final class OcrAgentDtos {

	private OcrAgentDtos() {
	}

	// OCR workflow는 파일 업로드 이후 프론트가 명시적으로 호출하는 후속 API입니다.
	// usage_statement 파일은 parse 후 DB 적재와 classification까지 진행합니다.
	@Schema(description = "사용내역서 OCR 파싱 요청")
	public record OcrUsageStatementParseRequest(
			@Schema(description = "사용내역서 파일 ID", example = "10")
			@NotNull(message = "fileId는 필수입니다.")
			Long fileId
	) {
	}

	// 증빙 파일은 이미 존재하는 usage_statement_item과 비교해야 하므로 statement/item id를 함께 받습니다.
	@Schema(description = "증빙 OCR 및 매칭 요청")
	public record OcrEvidenceMatchRequest(
			@Schema(description = "증빙 파일 ID", example = "10")
			@NotNull(message = "fileId는 필수입니다.")
			Long fileId,
			@Schema(description = "사용내역서 ID", example = "42")
			@NotNull(message = "usageStatementId는 필수입니다.")
			Long usageStatementId,
			@Schema(description = "사용내역서 상세항목 ID", example = "7")
			@NotNull(message = "usageStatementItemId는 필수입니다.")
			Long usageStatementItemId
	) {
	}

	@Schema(description = "OCR workflow 응답")
	public record OcrWorkflowResponse(
			String requestId,
			String workflow,
			String status,
			List<Long> validationLogIds,
			Long usageStatementId,
			Long evidenceFileLinkId,
			Map<String, Object> result
	) {
	}
}
