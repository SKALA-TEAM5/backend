package com.skala.backend.usage.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class UsageStatementResponses {

	private UsageStatementResponses() {
	}

	public record LatestUsageStatementResponse(Long projectId, UsageStatementDetailResponse statement) {
	}

	public record UsageStatementListResponse(Long projectId, List<UsageStatementListItemResponse> items) {
	}

	public record UsageStatementListItemResponse(
			Long id,
			LocalDate reportMonth,
			Integer revisionNo,
			LocalDate documentWrittenDate,
			BigDecimal cumulativeProgressRate,
			String statusCode,
			long summaryCount,
			long itemCount,
			long linkedEvidenceFileCount,
			long unsatisfiedRequirementCount
	) {
	}

	public record UsageStatementDetailDataResponse(Long projectId, UsageStatementDetailResponse statement) {
	}

	public record UsageStatementDetailResponse(
			Long id,
			LocalDate reportMonth,
			Integer revisionNo,
			LocalDate documentWrittenDate,
			BigDecimal cumulativeProgressRate,
			String statusCode,
			SourceFileResponse sourceFile,
			List<UsageStatementSummaryResponse> summaries,
			List<UsageStatementItemResponse> items
	) {
	}

	public record SourceFileResponse(
			Long fileId,
			String originalFilename,
			String evidenceTypeCode,
			String mimeType,
			Long sizeBytes,
			Instant uploadedAt
	) {
	}

	@Schema(description = "세부항목 추가 결과 (classi)")
	public record CreateItemResponse(
			@Schema(description = "classi가 카테고리를 변경했으면 true") boolean categoryChanged,
			@Schema(description = "변경된 항목 목록. 변경 없으면 빈 배열.") List<CreateItemResponse.CategoryChange> changes
	) {
		@Schema(description = "카테고리 변경 단건")
		public record CategoryChange(
				@Schema(description = "항목명") String itemName,
				@Schema(description = "변경 전 카테고리 코드") String fromCategoryCode,
				@Schema(description = "변경 전 카테고리명") String fromCategoryName,
				@Schema(description = "변경 후 카테고리 코드") String toCategoryCode,
				@Schema(description = "변경 후 카테고리명") String toCategoryName
		) {}
	}

	public record UsageStatementSummaryResponse(
			String categoryCode,
			String categoryName,
			BigDecimal previousAmount,
			BigDecimal currentAmount,
			BigDecimal cumulativeAmount
	) {
	}

	public record UsageStatementItemResponse(
			Long itemId,
			String categoryCode,
			String categoryName,
			LocalDate usedOn,
			String itemName,
			String unit,
			BigDecimal quantity,
			BigDecimal unitPrice,
			BigDecimal totalAmount,
			String remark,
			Integer pageNo,
			List<EvidenceFileResponse> evidenceFiles,
			List<RequirementResponse> requirements
	) {
	}

	public record EvidenceFileResponse(
			Long linkId,
			Long fileId,
			String evidenceTypeCode,
			String evidenceTypeName,
			String originalFilename,
			String mimeType,
			Long sizeBytes,
			Instant capturedAt,
			Instant uploadedAt,
			@Schema(description = "바운딩 박스 데이터. wearing_photo이고 vision agent 실행 완료 시에만 non-null.")
			com.skala.backend.file.dto.ProjectFileResponses.VisionDetections visionDetections
	) {
	}

	public record RequirementResponse(
			String evidenceTypeCode,
			String evidenceTypeName,
			boolean satisfied
	) {
	}

	public record UsageStatementStatusResponse(
			Long id,
			String statusCode
	) {
	}
}
