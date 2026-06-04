package com.skala.backend.agent.client;

import com.skala.backend.agent.dto.AgentResponses;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class FastApiAgentClientTest {

	// ─── mapReportResponse ────────────────────────────────────────────────
	// FastAPI OrchestratorActionResponse wrapper 언래핑 로직만 검증

	@Test
	void success_응답은_report와_reportDraft를_언래핑해서_반환한다() {
		Map<String, Object> raw = Map.of(
				"status", "success",
				"message", "report Agent 실행을 완료했습니다.",
				"result", Map.of(
						"report", Map.of(
								"agent_type_code", "report",
								"status_code", "success",
								"result_code", "success",
								"reason", "보고서 초안 생성 완료"
						),
						"reportDraft", Map.of("summary", "4월 안전관리비 사용내역 요약")
				)
		);

		AgentResponses.AgentRunResult result = FastApiAgentClient.mapReportResponse(raw);

		assertThat(result.agentTypeCode()).isEqualTo("report");
		assertThat(result.statusCode()).isEqualTo("success");
		assertThat(result.resultCode()).isEqualTo("success");
		assertThat(result.reason()).isEqualTo("보고서 초안 생성 완료");
		assertThat(result.reportDraft()).containsEntry("summary", "4월 안전관리비 사용내역 요약");
	}

	@Test
	void blocked_응답은_blocked_상태와_null_reportDraft로_반환한다() {
		Map<String, Object> raw = Map.of(
				"status", "blocked",
				"message", "legal 실행이 완료된 뒤 report 초안을 생성할 수 있습니다.",
				"result", Map.of()
		);

		AgentResponses.AgentRunResult result = FastApiAgentClient.mapReportResponse(raw);

		assertThat(result.agentTypeCode()).isEqualTo("report");
		assertThat(result.statusCode()).isEqualTo("blocked");
		assertThat(result.resultCode()).isEqualTo("fail");
		assertThat(result.reason()).isEqualTo("legal 실행이 완료된 뒤 report 초안을 생성할 수 있습니다.");
		assertThat(result.reportDraft()).isNull();
	}

	@Test
	void fail_응답은_fail_상태와_null_reportDraft로_반환한다() {
		Map<String, Object> raw = Map.of(
				"status", "fail",
				"message", "보고서 생성 중 오류가 발생했습니다.",
				"result", Map.of()
		);

		AgentResponses.AgentRunResult result = FastApiAgentClient.mapReportResponse(raw);

		assertThat(result.agentTypeCode()).isEqualTo("report");
		assertThat(result.statusCode()).isEqualTo("fail");
		assertThat(result.resultCode()).isEqualTo("fail");
		assertThat(result.reason()).isEqualTo("보고서 생성 중 오류가 발생했습니다.");
		assertThat(result.reportDraft()).isNull();
	}

	@Test
	void reportDraft가_없는_success_응답은_reportDraft가_null이다() {
		Map<String, Object> raw = Map.of(
				"status", "success",
				"message", "report Agent 실행을 완료했습니다.",
				"result", Map.of(
						"report", Map.of(
								"status_code", "success",
								"result_code", "hil",
								"reason", "사람 검토 필요"
						)
				)
		);

		AgentResponses.AgentRunResult result = FastApiAgentClient.mapReportResponse(raw);

		assertThat(result.statusCode()).isEqualTo("success");
		assertThat(result.resultCode()).isEqualTo("hil");
		assertThat(result.reportDraft()).isNull();
	}
}
