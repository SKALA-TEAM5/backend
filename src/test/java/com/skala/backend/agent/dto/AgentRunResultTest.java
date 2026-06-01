package com.skala.backend.agent.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AgentRunResultTest {

	private final ObjectMapper objectMapper = new ObjectMapper();

	// ─── 역직렬화 (FastAPI → Spring) ─────────────────────────────────────

	@Test
	void FastAPI_snake_case_응답을_올바르게_역직렬화한다() throws Exception {
		String json = """
				{
				  "agent_type_code": "vision",
				  "status_code": "success",
				  "result_code": "success",
				  "reason": "현장사진 안전시설 확인 완료"
				}
				""";

		AgentResponses.AgentRunResult result = objectMapper.readValue(json, AgentResponses.AgentRunResult.class);

		assertThat(result.agentTypeCode()).isEqualTo("vision");
		assertThat(result.statusCode()).isEqualTo("success");
		assertThat(result.resultCode()).isEqualTo("success");
		assertThat(result.reason()).isEqualTo("현장사진 안전시설 확인 완료");
	}

	@Test
	void result_code가_null인_경우도_역직렬화된다() throws Exception {
		String json = """
				{
				  "agent_type_code": "vision",
				  "status_code": "fail",
				  "result_code": null,
				  "reason": null
				}
				""";

		AgentResponses.AgentRunResult result = objectMapper.readValue(json, AgentResponses.AgentRunResult.class);

		assertThat(result.resultCode()).isNull();
		assertThat(result.reason()).isNull();
	}

	@Test
	void validate_응답_배열을_역직렬화한다() throws Exception {
		String json = """
				[
				  { "agent_type_code": "vision",     "status_code": "success", "result_code": "success", "reason": "확인 완료" },
				  { "agent_type_code": "link",        "status_code": "success", "result_code": "hil",     "reason": "금액 불일치" },
				  { "agent_type_code": "safety-doc",  "status_code": "success", "result_code": "success", "reason": "서류 충족" }
				]
				""";

		List<AgentResponses.AgentRunResult> results = objectMapper.readValue(
				json,
				objectMapper.getTypeFactory().constructCollectionType(List.class, AgentResponses.AgentRunResult.class)
		);

		assertThat(results).hasSize(3);
		assertThat(results.get(0).agentTypeCode()).isEqualTo("vision");
		assertThat(results.get(1).agentTypeCode()).isEqualTo("link");
		assertThat(results.get(1).resultCode()).isEqualTo("hil");
		assertThat(results.get(2).agentTypeCode()).isEqualTo("safety-doc");
	}

	// ─── 직렬화 (Spring → Frontend) ──────────────────────────────────────

	@Test
	void 직렬화_시_camelCase로_출력된다() throws Exception {
		AgentResponses.AgentRunResult result =
				new AgentResponses.AgentRunResult("legal", "success", "hil", "한도 초과");

		String json = objectMapper.writeValueAsString(result);

		assertThat(json).contains("\"agentTypeCode\"");
		assertThat(json).contains("\"statusCode\"");
		assertThat(json).contains("\"resultCode\"");
		assertThat(json).doesNotContain("\"agent_type_code\"");
		assertThat(json).doesNotContain("\"status_code\"");
		assertThat(json).doesNotContain("\"result_code\"");
	}
}
