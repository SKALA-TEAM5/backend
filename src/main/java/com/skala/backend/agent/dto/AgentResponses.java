package com.skala.backend.agent.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.skala.backend.agent.domain.AgentLog;
import com.skala.backend.agent.repository.AgentLogRepository;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

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

	@Schema(description = "사용내역서 파싱 결과")
	public record ParseResult(
			@Schema(description = "생성된 사용내역서 ID") Long usageStatementId,
			@Schema(description = "파싱된 세부항목 수") int itemCount
	) {}

	@Schema(description = "agent 동기 실행 결과 (validate / legal / report)")
	public record AgentRunResult(
			@JsonAlias("agent_type_code") @Schema(description = "에이전트 유형", example = "vision") String agentTypeCode,
			@JsonAlias("status_code")     @Schema(description = "실행 상태",    example = "success") String statusCode,
			@JsonAlias("result_code")     @Schema(description = "판단 결과",    example = "hil")     String resultCode,
			                              @Schema(description = "프론트 표시용 사유")                String reason,
			                              @Schema(description = "보고서 초안 (report agent 전용, 나머지는 null)") Map<String, Object> reportDraft
	) {}

	@Schema(description = "법령 검증 상세 조회 응답 (agent_logs.details 포함)")
	public record LegalDetailResponse(
			@Schema(description = "에이전트 유형", example = "legal") String agentTypeCode,
			@Schema(description = "실행 상태", example = "success") String statusCode,
			@Schema(description = "판단 결과", example = "hil") String resultCode,
			@Schema(description = "프론트 표시용 사유") String reason,
			@Schema(description = "법령 검증 내용 (JSON)") String details,
			@Schema(description = "생성일시") Instant createdAt
	) {
		public static LegalDetailResponse from(AgentLog log) {
			return new LegalDetailResponse(
					log.getAgentTypeCode(), log.getStatusCode(), log.getResultCode(),
					log.getReason(), log.getDetails(), log.getCreatedAt()
			);
		}
	}

	@Schema(description = "보고서 상세 조회 응답 (agent_logs.details 포함)")
	public record ReportDetailResponse(
			@Schema(description = "에이전트 유형", example = "report") String agentTypeCode,
			@Schema(description = "실행 상태", example = "success") String statusCode,
			@Schema(description = "판단 결과", example = "success") String resultCode,
			@Schema(description = "프론트 표시용 사유") String reason,
			@Schema(description = "보고서 내용 (JSON)") String details,
			@Schema(description = "생성일시") Instant createdAt
	) {
		public static ReportDetailResponse from(AgentLog log) {
			return new ReportDetailResponse(
					log.getAgentTypeCode(), log.getStatusCode(), log.getResultCode(),
					log.getReason(), log.getDetails(), log.getCreatedAt()
			);
		}
	}

	@Schema(description = "TODO 항목 단건")
	public record TodoItem(
			@Schema(description = "세부항목 ID (항목 단위가 아닌 경우 null)") Long usageStatementItemId,
			@Schema(description = "카테고리 코드", example = "CAT_03") String categoryCode,
			@Schema(description = "카테고리명", example = "안전시설비") String categoryName,
			@Schema(description = "세부항목명", example = "터널 환기덕트 안전시설 설치") String usageStatementItemName,
			@Schema(description = "조치 대상 증빙명 또는 요약 (reason의 구조화 버전)", example = "세금계산서") String title,
			@Schema(description = "필요 증빙 유형 코드 목록 (safety-doc 전용, 없으면 빈 배열)") List<String> evidenceTypeCodes,
			@Schema(description = "조치 사유") String reason
	) {}

	@Schema(description = "agent별 TODO 엔트리")
	public record AgentTodoEntry(
			@Schema(description = "에이전트 유형", example = "safety-doc") String agentTypeCode,
			@Schema(description = "판단 결과", example = "hil") String resultCode,
			@Schema(description = "요약 사유") String reason,
			@Schema(description = "TODO 항목 목록") List<TodoItem> items
	) {}

	@Schema(description = "TODO 목록 응답")
	public record TodoListResponse(
			@Schema(description = "validate 버튼 결과 TODO (safety-doc / link / vision 중 hil·fail만)") List<AgentTodoEntry> validate,
			@Schema(description = "legal 버튼 결과 TODO (없으면 null)") AgentTodoEntry legal
	) {}

	@Schema(description = "버튼 단건 상태")
	public record ButtonState(
			@Schema(description = "활성화 여부") boolean enabled,
			@Schema(description = "비활성화 사유 (활성화 시 null)") String reason
	) {}

	@Schema(description = "AI 버튼 상태 응답")
	public record ButtonStatesResponse(
			@Schema(description = "validate 버튼") ButtonState validate,
			@Schema(description = "legal 버튼")   ButtonState legal,
			@Schema(description = "report 버튼")  ButtonState report
	) {}

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
