package com.skala.backend.usage.controller;

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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class UsageStatementStatusControllerTest {

	@Autowired MockMvc mockMvc;
	@Autowired ObjectMapper objectMapper;
	@Autowired JdbcTemplate jdbcTemplate;
	@Autowired UserRepository userRepository;
	@Autowired PasswordEncoder passwordEncoder;

	// ─── R-33: 제출 (submit) ──────────────────────────────────────────────

	@Test
	void user_담당자는_draft_사용내역서를_제출할_수_있다() throws Exception {
		Cookie adminCookie = loginCookie(createUser("admin"));
		Map<String, String> user = createUser("user");
		Cookie userCookie = loginCookie(user);
		int userId = readUserId(user);
		int projectId = createProject(adminCookie);
		assign(adminCookie, projectId, userId);
		int statementId = insertStatement(projectId, "draft");

		mockMvc.perform(patch("/projects/{pid}/usage-statements/{sid}/submit", projectId, statementId)
						.cookie(userCookie))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.id").value(statementId))
				.andExpect(jsonPath("$.data.statusCode").value("upload_completed"));
	}

	@Test
	void admin은_draft_사용내역서를_제출할_수_있다() throws Exception {
		Cookie adminCookie = loginCookie(createUser("admin"));
		int projectId = createProject(adminCookie);
		int statementId = insertStatement(projectId, "draft");

		mockMvc.perform(patch("/projects/{pid}/usage-statements/{sid}/submit", projectId, statementId)
						.cookie(adminCookie))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.statusCode").value("upload_completed"));
	}

	@Test
	void 담당자가_아닌_user는_제출할_수_없다() throws Exception {
		Cookie adminCookie = loginCookie(createUser("admin"));
		Cookie outsiderCookie = loginCookie(createUser("user"));
		int projectId = createProject(adminCookie);
		int statementId = insertStatement(projectId, "draft");

		mockMvc.perform(patch("/projects/{pid}/usage-statements/{sid}/submit", projectId, statementId)
						.cookie(outsiderCookie))
				.andExpect(status().isForbidden());
	}

	@Test
	void 이미_제출된_사용내역서를_재제출하면_409를_반환한다() throws Exception {
		Cookie adminCookie = loginCookie(createUser("admin"));
		int projectId = createProject(adminCookie);
		int statementId = insertStatement(projectId, "upload_completed");

		mockMvc.perform(patch("/projects/{pid}/usage-statements/{sid}/submit", projectId, statementId)
						.cookie(adminCookie))
				.andExpect(status().isConflict());
	}

	@Test
	void supplement_required_상태에서_제출하면_409를_반환한다() throws Exception {
		Cookie adminCookie = loginCookie(createUser("admin"));
		int projectId = createProject(adminCookie);
		int statementId = insertStatement(projectId, "supplement_required");

		mockMvc.perform(patch("/projects/{pid}/usage-statements/{sid}/submit", projectId, statementId)
						.cookie(adminCookie))
				.andExpect(status().isConflict());
	}

	@Test
	void 존재하지_않는_사용내역서_제출은_404를_반환한다() throws Exception {
		Cookie adminCookie = loginCookie(createUser("admin"));
		int projectId = createProject(adminCookie);

		mockMvc.perform(patch("/projects/{pid}/usage-statements/{sid}/submit", projectId, 999_999_999L)
						.cookie(adminCookie))
				.andExpect(status().isNotFound());
	}

	@Test
	void 다른_프로젝트_사용내역서_제출은_404를_반환한다() throws Exception {
		Cookie adminCookie = loginCookie(createUser("admin"));
		int projectId = createProject(adminCookie);
		int otherProjectId = createProject(adminCookie);
		int otherStatementId = insertStatement(otherProjectId, "draft");

		mockMvc.perform(patch("/projects/{pid}/usage-statements/{sid}/submit", projectId, otherStatementId)
						.cookie(adminCookie))
				.andExpect(status().isNotFound());
	}

	// ─── R-34: 보완 요청 (requestSupplement) ─────────────────────────────

	@Test
	void admin은_법령_검토_완료_후_보완_요청을_할_수_있다() throws Exception {
		Cookie adminCookie = loginCookie(createUser("admin"));
		int projectId = createProject(adminCookie);
		int statementId = insertStatement(projectId, "upload_completed");
		insertLog(projectId, statementId, "legal", "success", "success");

		mockMvc.perform(patch("/projects/{pid}/usage-statements/{sid}/request-supplement", projectId, statementId)
						.cookie(adminCookie))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.statusCode").value("supplement_required"));
	}

	@Test
	void 법령_에이전트_hil_결과에서도_보완_요청을_할_수_있다() throws Exception {
		Cookie adminCookie = loginCookie(createUser("admin"));
		int projectId = createProject(adminCookie);
		int statementId = insertStatement(projectId, "upload_completed");
		insertLog(projectId, statementId, "legal", "success", "hil");

		mockMvc.perform(patch("/projects/{pid}/usage-statements/{sid}/request-supplement", projectId, statementId)
						.cookie(adminCookie))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.statusCode").value("supplement_required"));
	}

	@Test
	void 법령_에이전트_없이_보완_요청하면_409를_반환한다() throws Exception {
		Cookie adminCookie = loginCookie(createUser("admin"));
		int projectId = createProject(adminCookie);
		int statementId = insertStatement(projectId, "upload_completed");

		mockMvc.perform(patch("/projects/{pid}/usage-statements/{sid}/request-supplement", projectId, statementId)
						.cookie(adminCookie))
				.andExpect(status().isConflict());
	}

	@Test
	void 법령_에이전트_fail_결과에서_보완_요청하면_409를_반환한다() throws Exception {
		Cookie adminCookie = loginCookie(createUser("admin"));
		int projectId = createProject(adminCookie);
		int statementId = insertStatement(projectId, "upload_completed");
		insertLog(projectId, statementId, "legal", "success", "fail");

		mockMvc.perform(patch("/projects/{pid}/usage-statements/{sid}/request-supplement", projectId, statementId)
						.cookie(adminCookie))
				.andExpect(status().isConflict());
	}

	@Test
	void user는_보완_요청을_할_수_없다() throws Exception {
		Cookie adminCookie = loginCookie(createUser("admin"));
		Map<String, String> user = createUser("user");
		Cookie userCookie = loginCookie(user);
		int userId = readUserId(user);
		int projectId = createProject(adminCookie);
		assign(adminCookie, projectId, userId);
		int statementId = insertStatement(projectId, "upload_completed");

		mockMvc.perform(patch("/projects/{pid}/usage-statements/{sid}/request-supplement", projectId, statementId)
						.cookie(userCookie))
				.andExpect(status().isForbidden());
	}

	@Test
	void draft_상태에서_보완_요청하면_409를_반환한다() throws Exception {
		Cookie adminCookie = loginCookie(createUser("admin"));
		int projectId = createProject(adminCookie);
		int statementId = insertStatement(projectId, "draft");

		mockMvc.perform(patch("/projects/{pid}/usage-statements/{sid}/request-supplement", projectId, statementId)
						.cookie(adminCookie))
				.andExpect(status().isConflict());
	}

	@Test
	void 이미_supplement_required인_상태에서_재보완_요청하면_409를_반환한다() throws Exception {
		Cookie adminCookie = loginCookie(createUser("admin"));
		int projectId = createProject(adminCookie);
		int statementId = insertStatement(projectId, "supplement_required");

		mockMvc.perform(patch("/projects/{pid}/usage-statements/{sid}/request-supplement", projectId, statementId)
						.cookie(adminCookie))
				.andExpect(status().isConflict());
	}

	@Test
	void review_completed_상태에서_보완_요청하면_409를_반환한다() throws Exception {
		Cookie adminCookie = loginCookie(createUser("admin"));
		int projectId = createProject(adminCookie);
		int statementId = insertStatement(projectId, "review_completed");

		mockMvc.perform(patch("/projects/{pid}/usage-statements/{sid}/request-supplement", projectId, statementId)
						.cookie(adminCookie))
				.andExpect(status().isConflict());
	}

	// ─── R-35: 최종 승인 (completeReview) ────────────────────────────────

	@Test
	void admin은_법령_검토_완료_후_upload_completed_사용내역서를_최종_승인할_수_있다() throws Exception {
		Cookie adminCookie = loginCookie(createUser("admin"));
		int projectId = createProject(adminCookie);
		int statementId = insertStatement(projectId, "upload_completed");
		insertLog(projectId, statementId, "legal", "success", "success");

		mockMvc.perform(patch("/projects/{pid}/usage-statements/{sid}/complete-review", projectId, statementId)
						.cookie(adminCookie))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.statusCode").value("review_completed"));
	}

	@Test
	void admin은_법령_검토_완료_후_supplement_required_사용내역서를_최종_승인할_수_있다() throws Exception {
		Cookie adminCookie = loginCookie(createUser("admin"));
		int projectId = createProject(adminCookie);
		int statementId = insertStatement(projectId, "supplement_required");
		insertLog(projectId, statementId, "legal", "success", "hil");

		mockMvc.perform(patch("/projects/{pid}/usage-statements/{sid}/complete-review", projectId, statementId)
						.cookie(adminCookie))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.statusCode").value("review_completed"));
	}

	@Test
	void 법령_에이전트_없이_최종_승인하면_409를_반환한다() throws Exception {
		Cookie adminCookie = loginCookie(createUser("admin"));
		int projectId = createProject(adminCookie);
		int statementId = insertStatement(projectId, "upload_completed");

		mockMvc.perform(patch("/projects/{pid}/usage-statements/{sid}/complete-review", projectId, statementId)
						.cookie(adminCookie))
				.andExpect(status().isConflict());
	}

	@Test
	void 법령_에이전트_fail_결과에서_최종_승인하면_409를_반환한다() throws Exception {
		Cookie adminCookie = loginCookie(createUser("admin"));
		int projectId = createProject(adminCookie);
		int statementId = insertStatement(projectId, "upload_completed");
		insertLog(projectId, statementId, "legal", "success", "fail");

		mockMvc.perform(patch("/projects/{pid}/usage-statements/{sid}/complete-review", projectId, statementId)
						.cookie(adminCookie))
				.andExpect(status().isConflict());
	}

	@Test
	void user는_최종_승인을_할_수_없다() throws Exception {
		Cookie adminCookie = loginCookie(createUser("admin"));
		Map<String, String> user = createUser("user");
		Cookie userCookie = loginCookie(user);
		int userId = readUserId(user);
		int projectId = createProject(adminCookie);
		assign(adminCookie, projectId, userId);
		int statementId = insertStatement(projectId, "upload_completed");

		mockMvc.perform(patch("/projects/{pid}/usage-statements/{sid}/complete-review", projectId, statementId)
						.cookie(userCookie))
				.andExpect(status().isForbidden());
	}

	@Test
	void draft_상태에서_최종_승인하면_409를_반환한다() throws Exception {
		Cookie adminCookie = loginCookie(createUser("admin"));
		int projectId = createProject(adminCookie);
		int statementId = insertStatement(projectId, "draft");

		mockMvc.perform(patch("/projects/{pid}/usage-statements/{sid}/complete-review", projectId, statementId)
						.cookie(adminCookie))
				.andExpect(status().isConflict());
	}

	@Test
	void 이미_review_completed인_상태에서_재승인하면_409를_반환한다() throws Exception {
		Cookie adminCookie = loginCookie(createUser("admin"));
		int projectId = createProject(adminCookie);
		int statementId = insertStatement(projectId, "review_completed");

		mockMvc.perform(patch("/projects/{pid}/usage-statements/{sid}/complete-review", projectId, statementId)
						.cookie(adminCookie))
				.andExpect(status().isConflict());
	}

	// ─── R-33~R-35 전체 플로우 통합 ──────────────────────────────────────

	@Test
	void 사용내역서_제출_보완요청_최종승인_전체_플로우가_정상_동작한다() throws Exception {
		Cookie adminCookie = loginCookie(createUser("admin"));
		Map<String, String> user = createUser("user");
		Cookie userCookie = loginCookie(user);
		int userId = readUserId(user);
		int projectId = createProject(adminCookie);
		assign(adminCookie, projectId, userId);
		int statementId = insertStatement(projectId, "draft");

		// draft → upload_completed
		mockMvc.perform(patch("/projects/{pid}/usage-statements/{sid}/submit", projectId, statementId)
						.cookie(userCookie))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.statusCode").value("upload_completed"));

		// 법령 에이전트 완료
		insertLog(projectId, statementId, "legal", "success", "success");

		// upload_completed → supplement_required
		mockMvc.perform(patch("/projects/{pid}/usage-statements/{sid}/request-supplement", projectId, statementId)
						.cookie(adminCookie))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.statusCode").value("supplement_required"));

		// supplement_required → review_completed
		mockMvc.perform(patch("/projects/{pid}/usage-statements/{sid}/complete-review", projectId, statementId)
						.cookie(adminCookie))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.statusCode").value("review_completed"));
	}

	// ─── R-36: 프로젝트 카드 상태 뱃지 ──────────────────────────────────

	@Test
	void 사용내역서_없는_프로젝트는_상태_뱃지가_null이다() throws Exception {
		Cookie adminCookie = loginCookie(createUser("admin"));
		String prefix = "뱃지-없음-" + UUID.randomUUID();
		createProject(adminCookie, prefix);

		mockMvc.perform(get("/projects")
						.cookie(adminCookie)
						.param("projectName", prefix))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items[0].latestUsageStatementStatusCode").doesNotExist());
	}

	@Test
	void 제출_후_프로젝트_목록에_upload_completed_뱃지가_반영된다() throws Exception {
		Cookie adminCookie = loginCookie(createUser("admin"));
		String prefix = "뱃지-제출-" + UUID.randomUUID();
		int projectId = createProject(adminCookie, prefix);
		int statementId = insertStatement(projectId, "draft");

		mockMvc.perform(patch("/projects/{pid}/usage-statements/{sid}/submit", projectId, statementId)
						.cookie(adminCookie))
				.andExpect(status().isOk());

		mockMvc.perform(get("/projects")
						.cookie(adminCookie)
						.param("projectName", prefix))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items[0].latestUsageStatementStatusCode").value("upload_completed"));
	}

	@Test
	void 쿠키_없이_사용내역서_제출하면_401을_반환한다() throws Exception {
		Cookie adminCookie = loginCookie(createUser("admin"));
		int projectId = createProject(adminCookie);
		int statementId = insertStatement(projectId, "draft");

		mockMvc.perform(patch("/projects/{pid}/usage-statements/{sid}/submit", projectId, statementId))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void 여러_달_중_가장_최신_사용내역서_상태만_뱃지에_반영된다() throws Exception {
		Cookie adminCookie = loginCookie(createUser("admin"));
		String prefix = "뱃지-최신-" + UUID.randomUUID();
		int projectId = createProject(adminCookie, prefix);
		insertStatementWithMonth(projectId, "2026-03-01", "upload_completed");
		insertStatementWithMonth(projectId, "2026-05-01", "draft");

		mockMvc.perform(get("/projects")
						.cookie(adminCookie)
						.param("projectName", prefix))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items[0].latestUsageStatementStatusCode").value("draft"));
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
		return createProject(cookie, "테스트 프로젝트-" + UUID.randomUUID());
	}

	private int createProject(Cookie cookie, String projectName) throws Exception {
		MvcResult result = mockMvc.perform(post("/projects")
						.cookie(cookie)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of(
								"contractNo", "CN-" + UUID.randomUUID(),
								"constructionCompany", "스칼라건설",
								"projectName", projectName,
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

	private int insertStatement(int projectId, String statusCode) {
		return jdbcTemplate.queryForObject("""
				INSERT INTO service.usage_statements
					(project_id, report_month, revision_no, document_written_date, cumulative_progress_rate, status_code)
				VALUES (?, '2026-05-01'::date, 1, '2026-05-15'::date, 30, ?)
				RETURNING id
				""", Integer.class, projectId, statusCode);
	}

	private void insertStatementWithMonth(int projectId, String reportMonth, String statusCode) {
		jdbcTemplate.update("""
				INSERT INTO service.usage_statements
					(project_id, report_month, revision_no, document_written_date, cumulative_progress_rate, status_code)
				VALUES (?, ?::date, 1, ?::date, 30, ?)
				""", projectId, reportMonth, reportMonth, statusCode);
	}

	private void insertLog(int projectId, int statementId, String agentTypeCode, String statusCode, String resultCode) {
		jdbcTemplate.update("""
				INSERT INTO service.agent_logs
					(project_id, usage_statement_id, agent_type_code, status_code, result_code)
				VALUES (?, ?, ?, ?, ?)
				""", projectId, statementId, agentTypeCode, statusCode, resultCode);
	}
}
