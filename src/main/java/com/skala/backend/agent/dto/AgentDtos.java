package com.skala.backend.agent.dto;

import com.skala.backend.agent.domain.AgentLog;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class AgentDtos {

	private AgentDtos() {
	}

	@Schema(description = "Agent 실행 요청")
	public record AgentRunRequest(
			@Schema(description = "사용내역서 ID", example = "2008")
			@NotNull(message = "usageStatementId는 필수입니다.")
			Long usageStatementId,
			@Schema(description = "특정 항목만 실행할 때 사용하는 사용내역서 상세항목 ID", example = "10")
			Long usageStatementItemId,
			@Schema(description = "Agent별 선택 옵션")
			Map<String, Object> options
	) {
		public Map<String, Object> optionsOrEmpty() {
			return options == null ? Map.of() : options;
		}
	}

	@Schema(description = "Agent 실행 응답")
	public record AgentRunResponse(
			@Schema(description = "요청 추적 ID")
			String requestId,
			@Schema(description = "Agent 유형", example = "validator")
			String agentType,
			@Schema(description = "실행 상태", example = "succeeded")
			String status,
			@Schema(description = "저장된 로그 ID 목록")
			List<Long> logIds,
			@Schema(description = "Agent 실행 결과")
			Map<String, Object> result
	) {
	}

	@Schema(description = "agent_logs 조회 응답")
	public record AgentLogResponse(
			@Schema(description = "로그 ID") Long id,
			@Schema(description = "사용내역서 ID") Long usageStatementId,
			@Schema(description = "에이전트 유형", example = "vision") String agentTypeCode,
			@Schema(description = "실행 상태", example = "completed") String statusCode,
			@Schema(description = "사용 모델명") String modelName,
			@Schema(description = "배치 실행 단위 ID") UUID runId,
			@Schema(description = "생성일시") Instant createdAt
	) {
		public static AgentLogResponse from(AgentLog log) {
			return new AgentLogResponse(
					log.getId(),
					log.getUsageStatementId(),
					log.getAgentTypeCode(),
					log.getStatusCode(),
					log.getModelName(),
					log.getRunId(),
					log.getCreatedAt()
			);
		}
	}
}
