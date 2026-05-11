package com.skala.backend.agent.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class SafeleeAgentDtos {

	private SafeleeAgentDtos() {
	}

	public record EvidenceRequirementInputResponse(
			EvidenceRequirementItemContext itemContext,
			List<LinkedEvidenceFileContext> linkedFiles,
			List<EvidenceTypeDefinition> evidenceTypes
	) {
	}

	public record EvidenceRequirementItemContext(
			Long projectId,
			String projectName,
			Long usageStatementId,
			LocalDate reportMonth,
			Integer revisionNo,
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
			Integer pageNo
	) {
	}

	public record LinkedEvidenceFileContext(
			Long fileId,
			String originalFilename,
			String mimeType,
			String uploadedEvidenceTypeCode,
			String linkedEvidenceTypeCode,
			String storageKey,
			Instant capturedAt,
			Instant uploadedAt
	) {
	}

	public record EvidenceTypeDefinition(
			String code,
			String name,
			String description
	) {
	}

	@Schema(description = "AI 필수 증빙 판단 결과 저장 요청")
	public record EvidenceRequirementJudgementRequest(
			@NotNull(message = "requiredEvidences는 필수입니다.")
			List<String> requiredEvidences,
			BigDecimal confidence,
			String reason,
			String modelName
	) {
	}

	public record EvidenceRequirementJudgementResponse(
			Long itemId,
			List<EvidenceRequirementRecord> savedRequirements
	) {
	}

	public record EvidenceRequirementListResponse(
			Long itemId,
			List<EvidenceRequirementRecord> requirements
	) {
	}

	public record EvidenceRequirementRecord(
			String evidenceTypeCode,
			boolean isSatisfied,
			boolean isActive
	) {
	}
}
