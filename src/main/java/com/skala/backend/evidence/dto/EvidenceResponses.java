package com.skala.backend.evidence.dto;

import com.skala.backend.usage.dto.UsageStatementResponses.EvidenceFileResponse;
import com.skala.backend.usage.dto.UsageStatementResponses.RequirementResponse;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class EvidenceResponses {

	private EvidenceResponses() {
	}

	public record ItemEvidenceFilesResponse(
			Long projectId,
			Long itemId,
			List<EvidenceFileResponse> files,
			List<RequirementResponse> requirements
	) {
	}

	public record EvidenceLinkResponse(Long linkId) {
	}

	public record ArchiveCategoryListResponse(
			Long projectId,
			long uncheckedMatchedFileCount,
			List<ArchiveCategoryResponse> items
	) {
	}

	public record ArchiveCategoryResponse(
			String categoryCode,
			String categoryName,
			long itemCount,
			long linkedFileCount,
			long linkCount,
			long uncheckedMatchedFileCount,
			long unsatisfiedRequirementCount
	) {
	}

	public record ArchiveItemListResponse(Long projectId, String categoryCode, List<ArchiveItemResponse> items) {
	}

	public record ArchiveItemResponse(
			Long itemId,
			Long usageStatementId,
			LocalDate reportMonth,
			LocalDate usedOn,
			String itemName,
			String unit,
			BigDecimal quantity,
			BigDecimal unitPrice,
			BigDecimal totalAmount,
			String remark,
			Integer pageNo,
			long linkedFileCount,
			long uncheckedMatchedFileCount,
			long unsatisfiedRequirementCount
	) {
	}

	public record ArchiveMarkCheckedResponse(Long projectId, int checkedLinkCount) {
	}
}
