package com.skala.backend.agent.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AgentLogControllerTest {

	@Autowired MockMvc mockMvc;
	@Autowired ObjectMapper objectMapper;
	@Autowired JdbcTemplate jdbcTemplate;
	@Autowired UserRepository userRepository;
	@Autowired PasswordEncoder passwordEncoder;

	// ─── R-28: agent_logs 조회 ────────────────────────────────────────────

	@Test
	void runId로_해당_배치의_로그만_오름차순으로_조회한다() throws Exception {
		Cookie adminCookie = loginCookie(createUser("admin"));
		int projectId = createProject(adminCookie);
		int statementId = insertStatement(projectId);
		UUID runId = UUID.randomUUID();

		insertAgentLog(projectId, statementId, "vision", runId);
		insertAgentLog(projectId, statementId, "link", runId);
		insertAgentLog(projectId, statementId, "safety-doc", UUID.randomUUID()); // 다른 배치 로그

		mockMvc.perform(get("/projects/{pid}/agents/logs", projectId)
						.cookie(adminCookie)
						.param("runId", runId.toString()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data", hasSize(2)))
				.andExpect(jsonPath("$.data[0].agentTypeCode").value("vision"))
				.andExpect(jsonPath("$.data[0].runId").value(runId.toString()))
				.andExpect(jsonPath("$.data[1].agentTypeCode").value("link"));
	}

	@Test
	void usageStatementId로_해당_사용내역서의_로그를_내림차순으로_조회한다() throws Exception {
		Cookie adminCookie = loginCookie(createUser("admin"));
		int projectId = createProject(adminCookie);
		int statementId = insertStatement(projectId, "2026-05-01");
		int otherStatementId = insertStatement(projectId, "2026-04-01");

		insertAgentLog(projectId, statementId, "classi", null);
		insertAgentLog(projectId, statementId, "legal", null);
		insertAgentLog(projectId, otherStatementId, "classi", null); // 다른 사용내역서

		mockMvc.perform(get("/projects/{pid}/agents/logs", projectId)
						.cookie(adminCookie)
						.param("usageStatementId", String.valueOf(statementId)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data", hasSize(2)))
				.andExpect(jsonPath("$.data[0].usageStatementId").value(statementId))
				.andExpect(jsonPath("$.data[1].usageStatementId").value(statementId));
	}

	@Test
	void runId로_조회_시_다른_프로젝트의_같은_runId_로그는_제외된다() throws Exception {
		Cookie adminCookie = loginCookie(createUser("admin"));
		int projectId = createProject(adminCookie);
		int otherProjectId = createProject(adminCookie);
		int statementId = insertStatement(projectId);
		int otherStatementId = insertStatement(otherProjectId);
		UUID sharedRunId = UUID.randomUUID();

		insertAgentLog(projectId, statementId, "vision", sharedRunId);
		insertAgentLog(otherProjectId, otherStatementId, "link", sharedRunId); // 다른 프로젝트

		mockMvc.perform(get("/projects/{pid}/agents/logs", projectId)
						.cookie(adminCookie)
						.param("runId", sharedRunId.toString()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data", hasSize(1)))
				.andExpect(jsonPath("$.data[0].agentTypeCode").value("vision"));
	}

	@Test
	void 존재하지_않는_runId_조회_시_빈_배열을_반환한다() throws Exception {
		Cookie adminCookie = loginCookie(createUser("admin"));
		int projectId = createProject(adminCookie);

		mockMvc.perform(get("/projects/{pid}/agents/logs", projectId)
						.cookie(adminCookie)
						.param("runId", UUID.randomUUID().toString()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data", hasSize(0)));
	}

	@Test
	void runId와_usageStatementId_모두_없으면_400을_반환한다() throws Exception {
		Cookie adminCookie = loginCookie(createUser("admin"));
		int projectId = createProject(adminCookie);

		mockMvc.perform(get("/projects/{pid}/agents/logs", projectId)
						.cookie(adminCookie))
				.andExpect(status().isBadRequest());
	}

	@Test
	void 담당자가_아닌_user는_agent_로그를_조회할_수_없다() throws Exception {
		Cookie adminCookie = loginCookie(createUser("admin"));
		Cookie outsiderCookie = loginCookie(createUser("user"));
		int projectId = createProject(adminCookie);

		mockMvc.perform(get("/projects/{pid}/agents/logs", projectId)
						.cookie(outsiderCookie)
						.param("usageStatementId", "1"))
				.andExpect(status().isForbidden());
	}

	@Test
	void 쿠키_없이_agent_로그를_조회하면_401을_반환한다() throws Exception {
		Cookie adminCookie = loginCookie(createUser("admin"));
		int projectId = createProject(adminCookie);

		mockMvc.perform(get("/projects/{pid}/agents/logs", projectId)
						.param("usageStatementId", "1"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void 담당자_user는_agent_로그를_조회할_수_있다() throws Exception {
		Cookie adminCookie = loginCookie(createUser("admin"));
		Map<String, String> user = createUser("user");
		Cookie userCookie = loginCookie(user);
		int userId = readUserId(user);
		int projectId = createProject(adminCookie);
		assign(adminCookie, projectId, userId);
		int statementId = insertStatement(projectId);
		UUID runId = UUID.randomUUID();
		insertAgentLog(projectId, statementId, "classi", runId);

		mockMvc.perform(get("/projects/{pid}/agents/logs", projectId)
						.cookie(userCookie)
						.param("runId", runId.toString()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data", hasSize(1)));
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

	private int readUserId(Map<String, String> credentials) throws Exception {
		MvcResult result = mockMvc.perform(post("/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of(
								"employeeNo", credentials.get("employeeNo"),
								"password", credentials.get("password")
						))))
				.andExpect(status().isOk())
				.andReturn();
		return objectMapper.readTree(result.getResponse().getContentAsString())
				.path("data").path("user").path("id").asInt();
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
								"appropriatedAmount", 10_000_000,
								"status", "active"
						))))
				.andExpect(status().isCreated())
				.andReturn();
		return objectMapper.readTree(result.getResponse().getContentAsString())
				.path("data").path("project").path("id").asInt();
	}

	private void assign(Cookie cookie, int projectId, int userId) throws Exception {
		mockMvc.perform(post("/projects/{pid}/assignees/{uid}", projectId, userId).cookie(cookie))
				.andExpect(status().isOk());
	}

	private int insertStatement(int projectId) {
		return insertStatement(projectId, "2026-05-01");
	}

	private int insertStatement(int projectId, String reportMonth) {
		return jdbcTemplate.queryForObject("""
				INSERT INTO service.usage_statements
					(project_id, report_month, revision_no, document_written_date, cumulative_progress_rate)
				VALUES (?, ?::date, 1, ?::date, 30)
				RETURNING id
				""", Integer.class, projectId, reportMonth, reportMonth);
	}

	private void insertAgentLog(int projectId, int statementId, String agentTypeCode, UUID runId) {
		if (runId != null) {
			jdbcTemplate.update("""
					INSERT INTO service.agent_logs
						(project_id, usage_statement_id, agent_type_code, status_code, run_id)
					VALUES (?, ?, ?, 'completed', ?)
					""", projectId, statementId, agentTypeCode, runId);
		} else {
			jdbcTemplate.update("""
					INSERT INTO service.agent_logs
						(project_id, usage_statement_id, agent_type_code, status_code)
					VALUES (?, ?, ?, 'completed')
					""", projectId, statementId, agentTypeCode);
		}
	}
}
