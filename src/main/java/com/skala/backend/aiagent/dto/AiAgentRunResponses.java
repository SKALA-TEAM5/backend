package com.skala.backend.aiagent.dto;

import com.skala.backend.aiagent.domain.AiAgentRunStatusCode;
import com.skala.backend.aiagent.domain.AiAgentTypeCode;

public final class AiAgentRunResponses {

	private AiAgentRunResponses() {
	}

	public record AiAgentRunResponse(
			Long aiAgentRunId,
			Long projectId,
			AiAgentTypeCode agentTypeCode,
			AiAgentRunStatusCode statusCode,
			int resultCount
	) {
	}
}
