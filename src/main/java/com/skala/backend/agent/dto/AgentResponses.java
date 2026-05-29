package com.skala.backend.agent.dto;

import com.skala.backend.agent.domain.AgentLog;
import com.skala.backend.agent.repository.AgentLogRepository;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.time.LocalDate;

public final class AgentResponses {

	private AgentResponses() {}

	@Schema(description = "에이전트 경고 조회 응답")
	public record WarningResponse(
			@Schema(description = "로그 ID") Long agentLogId,
			@Schema(description = "에이전트 유형", example = "link") String agentTypeCode,
			@Schema(description = "실행 상태", example = "completed") String statusCode,
			@Schema(description = "사용내역서 ID") Long usageStatementId,
			@Schema(description = "보고월") LocalDate reportMonth,
			@Schema(description = "상세항목 ID") Long itemId,
			@Schema(description = "상세항목명") String itemName,
			@Schema(description = "카테고리 코드") String categoryCode,
			@Schema(description = "에이전트 상세 내용 (JSON)") String details,
			@Schema(description = "생성일시") Instant createdAt
	) {
		public static WarningResponse from(AgentLogRepository.AgentWarningRow row) {
			return new WarningResponse(
					row.getId(), row.getAgentTypeCode(), row.getStatusCode(),
					row.getUsageStatementId(), row.getReportMonth(),
					row.getUsageStatementItemId(), row.getItemName(),
					row.getCategoryCode(), row.getDetails(), row.getCreatedAt()
			);
		}
	}

	@Schema(description = "agent_logs 조회 응답")
	public record LogResponse(
			@Schema(description = "로그 ID") Long id,
			@Schema(description = "사용내역서 ID") Long usageStatementId,
			@Schema(description = "에이전트 유형", example = "vision") String agentTypeCode,
			@Schema(description = "실행 상태", example = "completed") String statusCode,
			@Schema(description = "사용 모델명") String modelName,
			@Schema(description = "생성일시") Instant createdAt
	) {
		public static LogResponse from(AgentLog log) {
			return new LogResponse(
					log.getId(), log.getUsageStatementId(), log.getAgentTypeCode(),
					log.getStatusCode(), log.getModelName(), log.getCreatedAt()
			);
		}
	}
}
