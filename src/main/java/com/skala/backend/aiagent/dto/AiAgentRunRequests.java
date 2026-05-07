package com.skala.backend.aiagent.dto;

import com.skala.backend.aiagent.domain.AiAgentTypeCode;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Map;

public final class AiAgentRunRequests {

	private AiAgentRunRequests() {
	}

	public record StartAiAgentRunRequest(
			@NotNull(message = "agentTypeCode는 필수입니다.")
			AiAgentTypeCode agentTypeCode,
			Long usageStatementId,
			List<Long> fileIds,
			Map<String, Object> options
	) {
	}
}
