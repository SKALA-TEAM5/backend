package com.skala.backend.agent.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.Map;

public final class LawAgentDtos {

	private LawAgentDtos() {
	}

	@Schema(description = "사용내역서 분류 agent 실행 요청")
	public record ClassificationRunRequest(
			Boolean rerun
	) {
		public boolean rerunOrFalse() {
			return Boolean.TRUE.equals(rerun);
		}
	}

	@Schema(description = "법령 검증 agent 실행 요청")
	public record ValidationRunRequest(
			String usageStatementId,
			Boolean rerun
	) {
		public boolean rerunOrFalse() {
			return Boolean.TRUE.equals(rerun);
		}
	}

	@Schema(description = "검증 결과 확인 요청")
	public record ValidationConfirmRequest(
			String decision,
			String comment
	) {
	}

	@Schema(description = "Law agent 실행 응답")
	public record LawAgentRunResponse(
			String workflow,
			String status,
			List<Long> validationLogIds,
			Map<String, Object> result
	) {
	}
}
