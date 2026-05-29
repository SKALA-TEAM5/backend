package com.skala.backend.agent.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public final class AgentRequests {

	private AgentRequests() {}

	@Schema(description = "사용내역서 OCR + classi 실행 요청")
	public record ParseRequest(
			@Schema(description = "업로드한 사용내역서 파일 ID", example = "10")
			@NotNull(message = "fileId는 필수입니다.") Long fileId
	) {}

	@Schema(description = "세부항목 classi 분류 요청")
	public record ClassifyRequest(
			@Schema(description = "사용내역서 ID", example = "42")
			@NotNull(message = "usageStatementId는 필수입니다.") Long usageStatementId,
			@Schema(description = "품목명", example = "안전모")
			@NotBlank(message = "itemName은 필수입니다.") String itemName,
			@Schema(description = "사용일자", example = "2026-04-15")
			@NotNull(message = "usedOn은 필수입니다.") LocalDate usedOn,
			@Schema(description = "단위", example = "개")
			String unit,
			@Schema(description = "수량", example = "10")
			@NotNull(message = "quantity는 필수입니다.") BigDecimal quantity,
			@Schema(description = "단가", example = "15000")
			@NotNull(message = "unitPrice는 필수입니다.") BigDecimal unitPrice,
			@Schema(description = "합계", example = "150000")
			@NotNull(message = "totalAmount는 필수입니다.") Long totalAmount
	) {}

	@Schema(description = "유효성 검증 실행 요청 (link + vision + safety_docs)")
	public record ValidateRequest(
			@Schema(description = "사용내역서 ID", example = "42")
			@NotNull(message = "usageStatementId는 필수입니다.") Long usageStatementId
	) {}
}
