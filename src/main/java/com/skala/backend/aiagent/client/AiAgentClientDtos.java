package com.skala.backend.aiagent.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.skala.backend.aiagent.domain.AiAgentRunStatusCode;
import com.skala.backend.aiagent.domain.AiAgentTypeCode;
import com.skala.backend.user.domain.RoleCode;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public final class AiAgentClientDtos {

	private AiAgentClientDtos() {
	}

	public record AiAgentClientRequest(
			Long aiAgentRunId,
			Long projectId,
			AiAgentTypeCode agentTypeCode,
			Long requestedByUserId,
			RoleCode requestedByRoleCode,
			Map<String, Object> context
	) {
	}

	public record AiAgentClientResponse(
			Long aiAgentRunId,
			AiAgentTypeCode agentTypeCode,
			AiAgentRunStatusCode statusCode,
			String errorMessage,
			List<AiAgentClientResult> results
	) {
	}

	public record AiAgentClientResult(
			Long fileId,
			Long usageStatementId,
			Long usageStatementItemId,
			String validationTypeCode,
			String logTypeCode,
			String severityCode,
			String resultCode,
			JsonNode details,
			String modelName
	) {
	}

	public record AgentFileContext(
			Long fileId,
			String uploadedEvidenceTypeCode,
			String originalFilename,
			String storageKey,
			String mimeType,
			Long sizeBytes,
			Instant capturedAt,
			Instant uploadedAt
	) {
	}

	public record AgentUsageStatementContext(
			Long usageStatementId,
			Long sourceFileId,
			LocalDate reportMonth,
			Integer revisionNo,
			LocalDate documentWrittenDate,
			Object cumulativeProgressRate
	) {
	}

	public record AgentUsageStatementItemContext(
			Long usageStatementItemId,
			Long usageStatementId,
			String categoryCode,
			LocalDate usedOn,
			String itemName,
			String unit,
			Object quantity,
			Object unitPrice,
			Object totalAmount,
			String remark,
			Integer pageNo
	) {
	}

	public record AgentEvidenceFileLinkContext(
			Long evidenceFileLinkId,
			Long usageStatementItemId,
			Long fileId,
			String categoryCode,
			String evidenceTypeCode,
			Instant checkedAt
	) {
	}
}
