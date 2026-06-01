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
	void usageStatementId로_해당_사용내역서의_로그를_내림차순으로_조회한다() throws Exception {
		Cookie adminCookie = loginCookie(createUser("admin"));
		int projectId = createProject(adminCookie);
		int statementId = insertStatement(projectId, "2026-05-01");
		int otherStatementId = insertStatement(projectId, "2026-04-01");

		insertAgentLog(projectId, statementId, "classi");
		insertAgentLog(projectId, statementId, "legal");
		insertAgentLog(projectId, otherStatementId, "classi"); // 다른 사용내역서

		mockMvc.perform(get("/projects/{pid}/agents/logs", projectId)
						.cookie(adminCookie)
						.param("usageStatementId", String.valueOf(statementId)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data", hasSize(2)))
				.andExpect(jsonPath("$.data[0].usageStatementId").value(statementId))
				.andExpect(jsonPath("$.data[1].usageStatementId").value(statementId));
	}

	@Test
	void usageStatementId_없이_조회하면_프로젝트_전체_로그를_반환한다() throws Exception {
		Cookie adminCookie = loginCookie(createUser("admin"));
		int projectId = createProject(adminCookie);
		int statementA = insertStatement(projectId, "2026-05-01");
		int statementB = insertStatement(projectId, "2026-04-01");

		insertAgentLog(projectId, statementA, "classi");
		insertAgentLog(projectId, statementB, "legal");

		mockMvc.perform(get("/projects/{pid}/agents/logs", projectId)
						.cookie(adminCookie))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data", hasSize(2)));
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
		insertAgentLog(projectId, statementId, "classi");

		mockMvc.perform(get("/projects/{pid}/agents/logs", projectId)
						.cookie(userCookie)
						.param("usageStatementId", String.valueOf(statementId)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data", hasSize(1)));
	}

	// ─── 에이전트 경고 조회 (/warnings) ───────────────────────────────────

	@Test
	void 항목_수준_경고_로그만_반환한다() throws Exception {
		Cookie adminCookie = loginCookie(createUser("admin"));
		int projectId = createProject(adminCookie);
		int statementId = insertStatement(projectId);
		int itemId = insertItem(statementId);

		insertWarningLog(projectId, statementId, "link", itemId);   // 경고 대상
		insertAgentLog(projectId, statementId, "classi");           // 정상 완료 — 제외

		mockMvc.perform(get("/projects/{pid}/agents/warnings", projectId)
						.cookie(adminCookie))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data", hasSize(1)))
				.andExpect(jsonPath("$.data[0].agentTypeCode").value("link"))
				.andExpect(jsonPath("$.data[0].itemId").value(itemId));
	}

	@Test
	void 실행_실패_로그도_경고로_반환한다() throws Exception {
		Cookie adminCookie = loginCookie(createUser("admin"));
		int projectId = createProject(adminCookie);
		int statementId = insertStatement(projectId);

		insertFailedLog(projectId, statementId, "vision");

		mockMvc.perform(get("/projects/{pid}/agents/warnings", projectId)
						.cookie(adminCookie))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data", hasSize(1)))
				.andExpect(jsonPath("$.data[0].statusCode").value("fail"));
	}

	@Test
	void 정상_완료_로그는_경고에_포함되지_않는다() throws Exception {
		Cookie adminCookie = loginCookie(createUser("admin"));
		int projectId = createProject(adminCookie);
		int statementId = insertStatement(projectId);

		insertAgentLog(projectId, statementId, "classi");
		insertAgentLog(projectId, statementId, "legal");

		mockMvc.perform(get("/projects/{pid}/agents/warnings", projectId)
						.cookie(adminCookie))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data", hasSize(0)));
	}

	@Test
	void usageStatementId_필터링이_동작한다() throws Exception {
		Cookie adminCookie = loginCookie(createUser("admin"));
		int projectId = createProject(adminCookie);
		int statementA = insertStatement(projectId, "2026-05-01");
		int statementB = insertStatement(projectId, "2026-04-01");
		int itemA = insertItem(statementA);
		int itemB = insertItem(statementB);

		insertWarningLog(projectId, statementA, "link", itemA);
		insertWarningLog(projectId, statementB, "vision", itemB);

		mockMvc.perform(get("/projects/{pid}/agents/warnings", projectId)
						.cookie(adminCookie)
						.param("usageStatementId", String.valueOf(statementA)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data", hasSize(1)))
				.andExpect(jsonPath("$.data[0].usageStatementId").value(statementA));
	}

	@Test
	void 다른_프로젝트의_경고는_포함되지_않는다() throws Exception {
		Cookie adminCookie = loginCookie(createUser("admin"));
		int projectId = createProject(adminCookie);
		int otherProjectId = createProject(adminCookie);
		int statementId = insertStatement(projectId);
		int otherStatementId = insertStatement(otherProjectId);
		int itemId = insertItem(statementId);
		int otherItemId = insertItem(otherStatementId);

		insertWarningLog(projectId, statementId, "link", itemId);
		insertWarningLog(otherProjectId, otherStatementId, "link", otherItemId);

		mockMvc.perform(get("/projects/{pid}/agents/warnings", projectId)
						.cookie(adminCookie))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data", hasSize(1)))
				.andExpect(jsonPath("$.data[0].usageStatementId").value(statementId));
	}

	@Test
	void 쿠키_없이_경고를_조회하면_401을_반환한다() throws Exception {
		Cookie adminCookie = loginCookie(createUser("admin"));
		int projectId = createProject(adminCookie);

		mockMvc.perform(get("/projects/{pid}/agents/warnings", projectId))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void 담당자가_아닌_user는_경고를_조회할_수_없다() throws Exception {
		Cookie adminCookie = loginCookie(createUser("admin"));
		Cookie outsiderCookie = loginCookie(createUser("user"));
		int projectId = createProject(adminCookie);

		mockMvc.perform(get("/projects/{pid}/agents/warnings", projectId)
						.cookie(outsiderCookie))
				.andExpect(status().isForbidden());
	}

	// ─── GET /agents/report ──────────────────────────────────────────────

	@Test
	void 보고서_로그가_있으면_details를_포함한_상세를_반환한다() throws Exception {
		Cookie adminCookie = loginCookie(createUser("admin"));
		int projectId = createProject(adminCookie);
		int statementId = insertStatement(projectId);
		insertReportLog(projectId, statementId, "success", "2026-05-01T00:00:00Z");

		mockMvc.perform(get("/projects/{pid}/agents/report", projectId)
						.cookie(adminCookie)
						.param("usageStatementId", String.valueOf(statementId)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.agentTypeCode").value("report"))
				.andExpect(jsonPath("$.data.statusCode").value("success"))
				.andExpect(jsonPath("$.data.details").isNotEmpty())
				.andExpect(jsonPath("$.data.createdAt").exists());
	}

	@Test
	void 보고서_로그가_없으면_404를_반환한다() throws Exception {
		Cookie adminCookie = loginCookie(createUser("admin"));
		int projectId = createProject(adminCookie);
		int statementId = insertStatement(projectId);

		mockMvc.perform(get("/projects/{pid}/agents/report", projectId)
						.cookie(adminCookie)
						.param("usageStatementId", String.valueOf(statementId)))
				.andExpect(status().isNotFound());
	}

	@Test
	void 보고서_조회_시_usageStatementId_누락하면_400을_반환한다() throws Exception {
		Cookie adminCookie = loginCookie(createUser("admin"));
		int projectId = createProject(adminCookie);

		mockMvc.perform(get("/projects/{pid}/agents/report", projectId)
						.cookie(adminCookie))
				.andExpect(status().isBadRequest());
	}

	@Test
	void 쿠키_없이_보고서를_조회하면_401을_반환한다() throws Exception {
		Cookie adminCookie = loginCookie(createUser("admin"));
		int projectId = createProject(adminCookie);
		int statementId = insertStatement(projectId);

		mockMvc.perform(get("/projects/{pid}/agents/report", projectId)
						.param("usageStatementId", String.valueOf(statementId)))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void 비담당자_user는_보고서를_조회할_수_없다() throws Exception {
		Cookie adminCookie = loginCookie(createUser("admin"));
		Cookie outsiderCookie = loginCookie(createUser("user"));
		int projectId = createProject(adminCookie);
		int statementId = insertStatement(projectId);
		insertReportLog(projectId, statementId, "success", "2026-05-01T00:00:00Z");

		mockMvc.perform(get("/projects/{pid}/agents/report", projectId)
						.cookie(outsiderCookie)
						.param("usageStatementId", String.valueOf(statementId)))
				.andExpect(status().isForbidden());
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

	private int insertItem(int statementId) {
		return jdbcTemplate.queryForObject("""
				INSERT INTO service.usage_statement_items
					(usage_statement_id, category_code, used_on, item_name, unit, quantity, unit_price, total_amount, page_no)
				VALUES (?, 'CAT_01', '2026-05-01', '안전관리자 임금', '월', 1, 500000, 500000, 1)
				RETURNING id
				""", Integer.class, statementId);
	}

	private void insertAgentLog(int projectId, int statementId, String agentTypeCode) {
		jdbcTemplate.update("""
				INSERT INTO service.agent_logs
					(project_id, usage_statement_id, agent_type_code, status_code)
				VALUES (?, ?, ?, 'success')
				""", projectId, statementId, agentTypeCode);
	}

	private void insertWarningLog(int projectId, int statementId, String agentTypeCode, int itemId) {
		jdbcTemplate.update("""
				INSERT INTO service.agent_logs
					(project_id, usage_statement_id, agent_type_code, status_code, usage_statement_item_id)
				VALUES (?, ?, ?, 'success', ?)
				""", projectId, statementId, agentTypeCode, itemId);
	}

	private void insertFailedLog(int projectId, int statementId, String agentTypeCode) {
		jdbcTemplate.update("""
				INSERT INTO service.agent_logs
					(project_id, usage_statement_id, agent_type_code, status_code)
				VALUES (?, ?, ?, 'fail')
				""", projectId, statementId, agentTypeCode);
	}

	private void insertReportLog(int projectId, int statementId, String statusCode, String createdAt) {
		jdbcTemplate.update("""
				INSERT INTO service.agent_logs
					(project_id, usage_statement_id, agent_type_code, status_code, details, created_at)
				VALUES (?, ?, 'report', ?, '{"summary": "보고서 생성 완료"}'::jsonb, ?::timestamptz)
				""", projectId, statementId, statusCode, createdAt);
	}
}
