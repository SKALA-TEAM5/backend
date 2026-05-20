package com.skala.backend.agent.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

public final class AgentRequests {

	private AgentRequests() {}

	@Schema(description = "Agent 실행 요청")
	public record RunRequest(
			@Schema(description = "사용내역서 ID", example = "2008")
			@NotNull(message = "usageStatementId는 필수입니다.") Long usageStatementId,
			@Schema(description = "특정 항목만 실행할 때 사용하는 사용내역서 상세항목 ID", example = "10")
			Long usageStatementItemId,
			@Schema(description = "Agent별 선택 옵션")
			Map<String, Object> options
	) {
		public Map<String, Object> optionsOrEmpty() {
			return options == null ? Map.of() : options;
		}
	}
}
