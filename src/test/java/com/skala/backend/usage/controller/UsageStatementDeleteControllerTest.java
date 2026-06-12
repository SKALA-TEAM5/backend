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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * DELETE /projects/{projectId}/usage-statements/{usageStatementId}
 * 사용내역서 삭제 및 연결 데이터 정리.
 *
 * <p>@Transactional 테스트라 트랜잭션이 롤백되므로 afterCommit(MinIO 회수)은 실행되지 않는다.
 * 따라서 여기서는 DB 단의 정리/보존만 검증한다(MinIO 회수는 best-effort라 비차단).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class UsageStatementDeleteControllerTest {

	@Autowired MockMvc mockMvc;
	@Autowired ObjectMapper objectMapper;
	@Autowired JdbcTemplate jdbcTemplate;
	@Autowired UserRepository userRepository;
	@Autowired PasswordEncoder passwordEncoder;

	@Test
	void admin은_사용내역서와_모든_연결_데이터를_삭제한다() throws Exception {
		Map<String, String> admin = createUser("admin");
		Cookie adminCookie = loginCookie(admin);
		int adminId = readUserId(admin);
		int projectId = createProject(adminCookie);

		int statementId = insertStatement(projectId, "review_completed");
		int itemId = insertItem(statementId);
		int sourceFile = insertFile(projectId, adminId, "src.pdf");
		jdbcTemplate.update("UPDATE service.usage_statements SET source_file_id = ? WHERE id = ?", sourceFile, statementId);
		int evidenceFile = insertFile(projectId, adminId, "evidence.jpg");
		insertLink(itemId, evidenceFile);
		insertRequirement(itemId);
		insertSummary(statementId);
		insertStatementLog(projectId, statementId, "legal");
		insertItemLog(projectId, statementId, itemId, "link");
		insertTodo(statementId, itemId);
		insertUsageRecord(projectId, adminId, statementId);

		mockMvc.perform(delete("/projects/{pid}/usage-statements/{sid}", projectId, statementId)
						.cookie(adminCookie))
				.andExpect(status().isOk());

		assertThat(count("usage_statements", "id = " + statementId)).isZero();
		assertThat(count("usage_statement_items", "usage_statement_id = " + statementId)).isZero();
		assertThat(count("usage_statement_summaries", "usage_statement_id = " + statementId)).isZero();
		assertThat(count("evidence_file_links", "usage_statement_item_id = " + itemId)).isZero();
		assertThat(count("evidence_requirements", "usage_statement_item_id = " + itemId)).isZero();
		assertThat(count("agent_logs", "usage_statement_id = " + statementId)).isZero();
		assertThat(count("agent_logs", "usage_statement_item_id = " + itemId)).isZero();
		assertThat(count("todos", "usage_statement_id = " + statementId)).isZero();
		// 전용 증빙 파일과 원본 PDF는 고아가 되어 DB에서 제거된다.
		assertThat(count("files", "id = " + evidenceFile)).isZero();
		assertThat(count("files", "id = " + sourceFile)).isZero();
	}

	@Test
	void 비용기록은_삭제되지_않고_statement_참조만_NULL로_끊긴다() throws Exception {
		Map<String, String> admin = createUser("admin");
		Cookie adminCookie = loginCookie(admin);
		int adminId = readUserId(admin);
		int projectId = createProject(adminCookie);
		int statementId = insertStatement(projectId, "upload_completed");
		int recordId = insertUsageRecord(projectId, adminId, statementId);

		mockMvc.perform(delete("/projects/{pid}/usage-statements/{sid}", projectId, statementId)
						.cookie(adminCookie))
				.andExpect(status().isOk());

		assertThat(count("agent_usage_records", "id = " + recordId)).isOne();
		assertThat(count("agent_usage_records", "id = " + recordId + " AND usage_statement_id IS NULL")).isOne();
	}

	@Test
	void 다른_사용내역서가_공유하는_파일은_보존된다() throws Exception {
		Map<String, String> admin = createUser("admin");
		Cookie adminCookie = loginCookie(admin);
		int adminId = readUserId(admin);
		int projectId = createProject(adminCookie);

		int statementA = insertStatement(projectId, "2026-05-01", "draft");
		int itemA = insertItem(statementA);
		int statementB = insertStatement(projectId, "2026-06-01", "draft");
		int itemB = insertItem(statementB);

		int sharedFile = insertFile(projectId, adminId, "shared.jpg");
		insertLink(itemA, sharedFile);
		insertLink(itemB, sharedFile);

		mockMvc.perform(delete("/projects/{pid}/usage-statements/{sid}", projectId, statementA)
						.cookie(adminCookie))
				.andExpect(status().isOk());

		// statementA의 링크만 끊기고, statementB가 여전히 참조하므로 파일은 보존된다.
		assertThat(count("evidence_file_links", "usage_statement_item_id = " + itemA)).isZero();
		assertThat(count("evidence_file_links", "usage_statement_item_id = " + itemB)).isOne();
		assertThat(count("files", "id = " + sharedFile)).isOne();
	}

	@Test
	void 담당_user는_삭제할_수_있다() throws Exception {
		Cookie adminCookie = loginCookie(createUser("admin"));
		Map<String, String> user = createUser("user");
		Cookie userCookie = loginCookie(user);
		int userId = readUserId(user);
		int projectId = createProject(adminCookie);
		assign(adminCookie, projectId, userId);
		int statementId = insertStatement(projectId, "draft");

		mockMvc.perform(delete("/projects/{pid}/usage-statements/{sid}", projectId, statementId)
						.cookie(userCookie))
				.andExpect(status().isOk());

		assertThat(count("usage_statements", "id = " + statementId)).isZero();
	}

	@Test
	void 담당자가_아닌_user는_삭제할_수_없다() throws Exception {
		Cookie adminCookie = loginCookie(createUser("admin"));
		Cookie outsiderCookie = loginCookie(createUser("user"));
		int projectId = createProject(adminCookie);
		int statementId = insertStatement(projectId, "draft");

		mockMvc.perform(delete("/projects/{pid}/usage-statements/{sid}", projectId, statementId)
						.cookie(outsiderCookie))
				.andExpect(status().isForbidden());
	}

	@Test
	void 존재하지_않는_사용내역서는_404() throws Exception {
		Cookie adminCookie = loginCookie(createUser("admin"));
		int projectId = createProject(adminCookie);

		mockMvc.perform(delete("/projects/{pid}/usage-statements/{sid}", projectId, 999999)
						.cookie(adminCookie))
				.andExpect(status().isNotFound());
	}

	// ─── helpers ──────────────────────────────────────────────────────────

	private long count(String table, String where) {
		return jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM service." + table + " WHERE " + where, Long.class);
	}

	private int insertStatement(int projectId, String statusCode) {
		return insertStatement(projectId, "2026-05-01", statusCode);
	}

	private int insertStatement(int projectId, String reportMonth, String statusCode) {
		return jdbcTemplate.queryForObject("""
				INSERT INTO service.usage_statements
					(project_id, report_month, revision_no, document_written_date, cumulative_progress_rate, status_code)
				VALUES (?, ?::date, 1, ?::date, 30, ?)
				RETURNING id
				""", Integer.class, projectId, reportMonth, reportMonth, statusCode);
	}

	private int insertItem(int statementId) {
		return jdbcTemplate.queryForObject("""
				INSERT INTO service.usage_statement_items
					(usage_statement_id, category_code, used_on, item_name, quantity, unit_price, total_amount, page_no)
				VALUES (?, 'CAT_01', '2026-05-10'::date, '테스트 항목', 1, 1000, 1000, 1)
				RETURNING id
				""", Integer.class, statementId);
	}

	private int insertFile(int projectId, int uploadedBy, String filename) {
		return jdbcTemplate.queryForObject("""
				INSERT INTO service.files
					(project_id, uploaded_by_user_id, uploaded_evidence_type_code, original_filename, storage_key, mime_type, size_bytes)
				VALUES (?, ?, 'receipt', ?, ?, 'image/jpeg', 100)
				RETURNING id
				""", Integer.class, projectId, uploadedBy, filename, "key/" + UUID.randomUUID());
	}

	private void insertLink(int itemId, int fileId) {
		jdbcTemplate.update("""
				INSERT INTO service.evidence_file_links (usage_statement_item_id, file_id, evidence_type_code)
				VALUES (?, ?, 'receipt')
				""", itemId, fileId);
	}

	private void insertRequirement(int itemId) {
		jdbcTemplate.update("""
				INSERT INTO service.evidence_requirements (usage_statement_item_id, evidence_type_code, is_satisfied, is_active)
				VALUES (?, 'receipt', false, true)
				""", itemId);
	}

	private void insertSummary(int statementId) {
		jdbcTemplate.update("""
				INSERT INTO service.usage_statement_summaries
					(usage_statement_id, category_code, previous_amount, current_amount, cumulative_amount)
				VALUES (?, 'CAT_01', 0, 1000, 1000)
				""", statementId);
	}

	private void insertStatementLog(int projectId, int statementId, String agentTypeCode) {
		jdbcTemplate.update("""
				INSERT INTO service.agent_logs
					(project_id, usage_statement_id, agent_type_code, status_code, result_code)
				VALUES (?, ?, ?, 'success', 'success')
				""", projectId, statementId, agentTypeCode);
	}

	private void insertItemLog(int projectId, int statementId, int itemId, String agentTypeCode) {
		jdbcTemplate.update("""
				INSERT INTO service.agent_logs
					(project_id, usage_statement_id, usage_statement_item_id, agent_type_code, status_code, result_code)
				VALUES (?, ?, ?, ?, 'success', 'hil')
				""", projectId, statementId, itemId, agentTypeCode);
	}

	private void insertTodo(int statementId, int itemId) {
		jdbcTemplate.update("""
				INSERT INTO service.todos
					(usage_statement_id, usage_statement_item_id, agent_type_code, reason, todo_key, confirmed)
				VALUES (?, ?, 'link', '증빙 누락', ?, false)
				""", statementId, itemId, UUID.randomUUID().toString());
	}

	private int insertUsageRecord(int projectId, int userId, int statementId) {
		return jdbcTemplate.queryForObject("""
				INSERT INTO service.agent_usage_records
					(user_id, project_id, usage_statement_id, agent_type_code, input_tokens, output_tokens, cost_usd)
				VALUES (?, ?, ?, 'legal', 100, 50, 0.001)
				RETURNING id
				""", Integer.class, userId, projectId, statementId);
	}

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
}
