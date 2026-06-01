package com.skala.backend.agent.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skala.backend.agent.client.FastApiAgentClient;
import com.skala.backend.agent.dto.AgentResponses;
import com.skala.backend.user.domain.RoleCode;
import com.skala.backend.user.domain.User;
import com.skala.backend.user.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AgentRunControllerTest {

	@Autowired MockMvc mockMvc;
	@Autowired ObjectMapper objectMapper;
	@Autowired JdbcTemplate jdbcTemplate;
	@Autowired UserRepository userRepository;
	@Autowired PasswordEncoder passwordEncoder;

	@MockitoBean
	FastApiAgentClient fastApiAgentClient;

	// ─── POST /agents/parse ───────────────────────────────────────────────
	// parse는 동기 호출 — FastAPI 응답(~2s)을 기다린 뒤 usageStatementId/itemCount 반환

	@Test
	void parse_성공_시_usageStatementId와_itemCount를_반환한다() throws Exception {
		Cookie cookie = loginCookie(createUser("admin"));
		int projectId = createProject(cookie);

		when(fastApiAgentClient.parseUsageStatement(anyLong(), anyLong()))
				.thenReturn(new AgentResponses.ParseResult(42L, 15));

		mockMvc.perform(post("/projects/{pid}/agents/parse", projectId)
						.cookie(cookie)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of("fileId", 10))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.usageStatementId").value(42))
				.andExpect(jsonPath("$.data.itemCount").value(15));

		verify(fastApiAgentClient).parseUsageStatement((long) projectId, 10L);
	}

	@Test
	void parse_fileId_누락_시_400을_반환한다() throws Exception {
		Cookie cookie = loginCookie(createUser("admin"));
		int projectId = createProject(cookie);

		mockMvc.perform(post("/projects/{pid}/agents/parse", projectId)
						.cookie(cookie)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void parse_미인증_요청은_401을_반환한다() throws Exception {
		Cookie cookie = loginCookie(createUser("admin"));
		int projectId = createProject(cookie);

		mockMvc.perform(post("/projects/{pid}/agents/parse", projectId)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of("fileId", 10))))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void parse_프로젝트_비담당_user는_403을_반환한다() throws Exception {
		Cookie adminCookie = loginCookie(createUser("admin"));
		Cookie outsiderCookie = loginCookie(createUser("user"));
		int projectId = createProject(adminCookie);

		mockMvc.perform(post("/projects/{pid}/agents/parse", projectId)
						.cookie(outsiderCookie)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of("fileId", 10))))
				.andExpect(status().isForbidden());
	}

	@Test
	void parse_FastAPI_장애_시_503을_반환한다() throws Exception {
		Cookie cookie = loginCookie(createUser("admin"));
		int projectId = createProject(cookie);

		doThrow(new RestClientException("connection refused"))
				.when(fastApiAgentClient).parseUsageStatement(anyLong(), anyLong());

		mockMvc.perform(post("/projects/{pid}/agents/parse", projectId)
						.cookie(cookie)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of("fileId", 10))))
				.andExpect(status().isServiceUnavailable());
	}

	// ─── POST /agents/validate ────────────────────────────────────────────
	// validate는 동기 — FastAPI 완료까지 대기, 3개 agent 결과 배열 반환

	@Test
	void validate_성공_시_3개_agent_결과를_반환한다() throws Exception {
		Cookie cookie = loginCookie(createUser("admin"));
		int projectId = createProject(cookie);
		int statementId = insertStatement(projectId);

		when(fastApiAgentClient.runValidation(anyLong(), anyLong(), anyLong()))
				.thenReturn(List.of(
						new AgentResponses.AgentRunResult("vision",     "success", "success", "현장사진 확인 완료"),
						new AgentResponses.AgentRunResult("link",       "success", "hil",     "금액 불일치"),
						new AgentResponses.AgentRunResult("safety-doc", "success", "success", "필수 서류 충족")
				));

		mockMvc.perform(post("/projects/{pid}/agents/validate", projectId)
						.cookie(cookie)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of("usageStatementId", statementId))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data").isArray())
				.andExpect(jsonPath("$.data[0].agentTypeCode").value("vision"))
				.andExpect(jsonPath("$.data[0].statusCode").value("success"))
				.andExpect(jsonPath("$.data[0].resultCode").value("success"))
				.andExpect(jsonPath("$.data[1].agentTypeCode").value("link"))
				.andExpect(jsonPath("$.data[1].resultCode").value("hil"))
				.andExpect(jsonPath("$.data[2].agentTypeCode").value("safety-doc"));

		verify(fastApiAgentClient).runValidation(anyLong(), anyLong(), anyLong());
	}

	@Test
	void validate_usageStatementId_누락_시_400을_반환한다() throws Exception {
		Cookie cookie = loginCookie(createUser("admin"));
		int projectId = createProject(cookie);

		mockMvc.perform(post("/projects/{pid}/agents/validate", projectId)
						.cookie(cookie)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void validate_미인증_요청은_401을_반환한다() throws Exception {
		Cookie cookie = loginCookie(createUser("admin"));
		int projectId = createProject(cookie);

		mockMvc.perform(post("/projects/{pid}/agents/validate", projectId)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of("usageStatementId", 1))))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void validate_FastAPI_장애_시_503을_반환한다() throws Exception {
		Cookie cookie = loginCookie(createUser("admin"));
		int projectId = createProject(cookie);
		int statementId = insertStatement(projectId);

		doThrow(new RestClientException("connection refused"))
				.when(fastApiAgentClient).runValidation(anyLong(), anyLong(), anyLong());

		mockMvc.perform(post("/projects/{pid}/agents/validate", projectId)
						.cookie(cookie)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of("usageStatementId", statementId))))
				.andExpect(status().isServiceUnavailable());
	}

	// ─── POST /agents/legal ───────────────────────────────────────────────
	// legal은 동기 — FastAPI 완료까지 대기, agent 결과 단건 반환

	@Test
	void legal_성공_시_agent_결과를_반환한다() throws Exception {
		Cookie cookie = loginCookie(createUser("admin"));
		int projectId = createProject(cookie);
		int statementId = insertStatement(projectId);

		when(fastApiAgentClient.runLegal(anyLong(), anyLong(), anyLong()))
				.thenReturn(new AgentResponses.AgentRunResult("legal", "success", "hil", "한도 초과 항목 발견"));

		mockMvc.perform(post("/projects/{pid}/agents/legal", projectId)
						.cookie(cookie)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of("usageStatementId", statementId))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.agentTypeCode").value("legal"))
				.andExpect(jsonPath("$.data.statusCode").value("success"))
				.andExpect(jsonPath("$.data.resultCode").value("hil"))
				.andExpect(jsonPath("$.data.reason").value("한도 초과 항목 발견"));

		verify(fastApiAgentClient).runLegal(anyLong(), anyLong(), anyLong());
	}

	@Test
	void legal_usageStatementId_누락_시_400을_반환한다() throws Exception {
		Cookie cookie = loginCookie(createUser("admin"));
		int projectId = createProject(cookie);

		mockMvc.perform(post("/projects/{pid}/agents/legal", projectId)
						.cookie(cookie)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void legal_미인증_요청은_401을_반환한다() throws Exception {
		Cookie cookie = loginCookie(createUser("admin"));
		int projectId = createProject(cookie);

		mockMvc.perform(post("/projects/{pid}/agents/legal", projectId)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of("usageStatementId", 1))))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void legal_FastAPI_장애_시_503을_반환한다() throws Exception {
		Cookie cookie = loginCookie(createUser("admin"));
		int projectId = createProject(cookie);
		int statementId = insertStatement(projectId);

		doThrow(new RestClientException("connection refused"))
				.when(fastApiAgentClient).runLegal(anyLong(), anyLong(), anyLong());

		mockMvc.perform(post("/projects/{pid}/agents/legal", projectId)
						.cookie(cookie)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of("usageStatementId", statementId))))
				.andExpect(status().isServiceUnavailable());
	}

	// ─── POST /agents/report ──────────────────────────────────────────────
	// report는 동기 — FastAPI 완료까지 대기, agent 결과 단건 반환

	@Test
	void report_성공_시_agent_결과를_반환한다() throws Exception {
		Cookie cookie = loginCookie(createUser("admin"));
		int projectId = createProject(cookie);
		int statementId = insertStatement(projectId);

		when(fastApiAgentClient.runReport(anyLong(), anyLong(), anyLong()))
				.thenReturn(new AgentResponses.AgentRunResult("report", "success", "success", "보고서 생성 완료"));

		mockMvc.perform(post("/projects/{pid}/agents/report", projectId)
						.cookie(cookie)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of("usageStatementId", statementId))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.agentTypeCode").value("report"))
				.andExpect(jsonPath("$.data.statusCode").value("success"))
				.andExpect(jsonPath("$.data.resultCode").value("success"))
				.andExpect(jsonPath("$.data.reason").value("보고서 생성 완료"));

		verify(fastApiAgentClient).runReport(anyLong(), anyLong(), anyLong());
	}

	@Test
	void report_usageStatementId_누락_시_400을_반환한다() throws Exception {
		Cookie cookie = loginCookie(createUser("admin"));
		int projectId = createProject(cookie);

		mockMvc.perform(post("/projects/{pid}/agents/report", projectId)
						.cookie(cookie)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void report_미인증_요청은_401을_반환한다() throws Exception {
		Cookie cookie = loginCookie(createUser("admin"));
		int projectId = createProject(cookie);

		mockMvc.perform(post("/projects/{pid}/agents/report", projectId)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of("usageStatementId", 1))))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void report_FastAPI_장애_시_503을_반환한다() throws Exception {
		Cookie cookie = loginCookie(createUser("admin"));
		int projectId = createProject(cookie);
		int statementId = insertStatement(projectId);

		doThrow(new RestClientException("connection refused"))
				.when(fastApiAgentClient).runReport(anyLong(), anyLong(), anyLong());

		mockMvc.perform(post("/projects/{pid}/agents/report", projectId)
						.cookie(cookie)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of("usageStatementId", statementId))))
				.andExpect(status().isServiceUnavailable());
	}

	// ─── fixtures ─────────────────────────────────────────────────────────

	private Map<String, String> createUser(String roleCode) {
		Map<String, String> credentials = Map.of(
				"employeeNo", "EMP-" + UUID.randomUUID(),
				"realName", "홍길동",
				"password", "P@ssw0rd123!",
				"roleCode", roleCode
		);
		userRepository.saveAndFlush(User.create(
				credentials.get("employeeNo"),
				credentials.get("realName"),
				passwordEncoder.encode(credentials.get("password")),
				RoleCode.from(roleCode)
		));
		return credentials;
	}

	private Cookie loginCookie(Map<String, String> credentials) throws Exception {
		MvcResult result = mockMvc.perform(post("/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of(
								"employeeNo", credentials.get("employeeNo"),
								"password", credentials.get("password")
						))))
				.andExpect(status().isOk())
				.andReturn();
		return result.getResponse().getCookie("access_token");
	}

	private int createProject(Cookie cookie) throws Exception {
		MvcResult result = mockMvc.perform(post("/projects")
						.cookie(cookie)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of(
								"contractNo", "CN-" + UUID.randomUUID(),
								"constructionCompany", "스칼라건설",
								"projectName", "테스트 프로젝트-" + UUID.randomUUID(),
								"siteLocation", "서울시 강남구",
								"contractAmount", 100_000_000,
								"constructionStartDate", "2026-01-01",
								"constructionEndDate", "2026-12-31",
								"appropriatedAmount", 10_000_000
						))))
				.andExpect(status().isCreated())
				.andReturn();
		return objectMapper.readTree(result.getResponse().getContentAsString())
				.path("data").path("project").path("id").asInt();
	}

	private int insertStatement(int projectId) {
		return jdbcTemplate.queryForObject("""
				INSERT INTO service.usage_statements
					(project_id, report_month, revision_no, document_written_date, cumulative_progress_rate)
				VALUES (?, '2026-05-01', 1, '2026-05-01', 30)
				RETURNING id
				""", Integer.class, projectId);
	}
}
