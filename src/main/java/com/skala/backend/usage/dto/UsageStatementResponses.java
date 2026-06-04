package com.skala.backend.usage.dto;

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

	public record CreateItemResponse(
			Long itemId,
			String requestedCategoryCode,
			String assignedCategoryCode,
			boolean categoryChanged
	) {
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
			Instant uploadedAt
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
