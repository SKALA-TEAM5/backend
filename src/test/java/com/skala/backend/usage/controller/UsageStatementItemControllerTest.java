package com.skala.backend.usage.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skala.backend.agent.client.FastApiAgentClient;
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

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class UsageStatementItemControllerTest {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	ObjectMapper objectMapper;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Autowired
	UserRepository userRepository;

	@Autowired
	PasswordEncoder passwordEncoder;

	@MockitoBean
	FastApiAgentClient fastApiAgentClient;

	@BeforeEach
	void stubClassifyItem() {
		lenient().when(fastApiAgentClient.classifyItem(anyLong(), anyLong(), anyString(), anyString(), any(), any(), any(), any(), any()))
				.thenReturn(new FastApiAgentClient.ClassifyResult(99L, "CAT_01"));
	}

	// ─── R-12: 세부항목 수동 추가 ───────────────────────────────────────

	@Test
	void user_담당자는_세부항목을_수동_추가할_수_있다() throws Exception {
		Cookie managerCookie = loginCookie(createUser("admin"));
		Map<String, String> user = createUser("user");
		Cookie userCookie = loginCookie(user);
		int userId = readUserIdFromLogin(user);
		int projectId = createProject(managerCookie);
		assign(managerCookie, projectId, userId);
		int statementId = insertUsageStatement(projectId);

		mockMvc.perform(post("/projects/{pid}/usage-statements/{sid}/items", projectId, statementId)
						.cookie(userCookie)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(defaultItemRequest())))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.itemId").isNumber())
				.andExpect(jsonPath("$.data.requestedCategoryCode").value("CAT_01"))
				.andExpect(jsonPath("$.data.assignedCategoryCode").value("CAT_01"))
				.andExpect(jsonPath("$.data.categoryChanged").value(false));

		verify(fastApiAgentClient).classifyItem(anyLong(), anyLong(), anyString(), anyString(), any(), any(), any(), any(), any());
	}

	@Test
	void classi가_카테고리를_변경하면_categoryChanged_true를_반환한다() throws Exception {
		Cookie managerCookie = loginCookie(createUser("admin"));
		int projectId = createProject(managerCookie);
		int statementId = insertUsageStatement(projectId);

		lenient().when(fastApiAgentClient.classifyItem(anyLong(), anyLong(), anyString(), anyString(), any(), any(), any(), any(), any()))
				.thenReturn(new FastApiAgentClient.ClassifyResult(99L, "CAT_02"));

		mockMvc.perform(post("/projects/{pid}/usage-statements/{sid}/items", projectId, statementId)
						.cookie(managerCookie)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(defaultItemRequest())))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.requestedCategoryCode").value("CAT_01"))
				.andExpect(jsonPath("$.data.assignedCategoryCode").value("CAT_02"))
				.andExpect(jsonPath("$.data.categoryChanged").value(true));
	}

	@Test
	void admin은_세부항목을_수동_추가할_수_있다() throws Exception {
		Cookie managerCookie = loginCookie(createUser("admin"));
		int projectId = createProject(managerCookie);
		int statementId = insertUsageStatement(projectId);

		mockMvc.perform(post("/projects/{pid}/usage-statements/{sid}/items", projectId, statementId)
						.cookie(managerCookie)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(defaultItemRequest())))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.itemId").isNumber());
	}

	@Test
	void 존재하지_않는_카테고리로_항목_추가를_거부한다() throws Exception {
		Cookie managerCookie = loginCookie(createUser("admin"));
		int projectId = createProject(managerCookie);
		int statementId = insertUsageStatement(projectId);

		Map<String, Object> request = itemRequest("INVALID_CAT", "안전모", 1, 1000, 1000, 1);

		mockMvc.perform(post("/projects/{pid}/usage-statements/{sid}/items", projectId, statementId)
						.cookie(managerCookie)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("존재하지 않는 카테고리 코드입니다."));
	}

	@Test
	void 다른_프로젝트의_사용내역서에_항목_추가를_거부한다() throws Exception {
		Cookie managerCookie = loginCookie(createUser("admin"));
		int projectId = createProject(managerCookie);
		int otherProjectId = createProject(managerCookie);
		int statementId = insertUsageStatement(otherProjectId);

		mockMvc.perform(post("/projects/{pid}/usage-statements/{sid}/items", projectId, statementId)
						.cookie(managerCookie)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(defaultItemRequest())))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.message").value("사용내역서를 찾을 수 없습니다."));
	}

	@Test
	void 담당자가_아닌_user는_항목을_추가할_수_없다() throws Exception {
		Cookie managerCookie = loginCookie(createUser("admin"));
		Cookie outsiderCookie = loginCookie(createUser("user"));
		int projectId = createProject(managerCookie);
		int statementId = insertUsageStatement(projectId);

		mockMvc.perform(post("/projects/{pid}/usage-statements/{sid}/items", projectId, statementId)
						.cookie(outsiderCookie)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(defaultItemRequest())))
				.andExpect(status().isForbidden());
	}

	@Test
	void 인증_없이_세부항목을_추가할_수_없다() throws Exception {
		Cookie managerCookie = loginCookie(createUser("admin"));
		int projectId = createProject(managerCookie);
		int statementId = insertUsageStatement(projectId);

		mockMvc.perform(post("/projects/{pid}/usage-statements/{sid}/items", projectId, statementId)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(defaultItemRequest())))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void 세부항목_추가_필수값_누락을_거부한다() throws Exception {
		Cookie managerCookie = loginCookie(createUser("admin"));
		int projectId = createProject(managerCookie);
		int statementId = insertUsageStatement(projectId);

		// categoryCode + usedOn만 보내면 itemName, quantity, unitPrice 등 필수값 누락으로 400
		mockMvc.perform(post("/projects/{pid}/usage-statements/{sid}/items", projectId, statementId)
						.cookie(managerCookie)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of(
								"categoryCode", "CAT_01",
								"usedOn", "2026-05-01"
						))))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.success").value(false));
	}

	@Test
	void 세부항목_추가_시_수량과_금액은_양수여야_한다() throws Exception {
		Cookie managerCookie = loginCookie(createUser("admin"));
		int projectId = createProject(managerCookie);
		int statementId = insertUsageStatement(projectId);

		mockMvc.perform(post("/projects/{pid}/usage-statements/{sid}/items", projectId, statementId)
						.cookie(managerCookie)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of(
								"categoryCode", "CAT_01",
								"usedOn", "2026-05-01",
								"itemName", "안전모",
								"quantity", -1,
								"unitPrice", 1000,
								"totalAmount", 1000,
								"pageNo", 1
						))))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value(containsString("quantity:")));
	}

	// ─── R-13: 세부항목 수정 ────────────────────────────────────────────

	@Test
	void 세부항목을_수정할_수_있다() throws Exception {
		Cookie managerCookie = loginCookie(createUser("admin"));
		int projectId = createProject(managerCookie);
		int statementId = insertUsageStatement(projectId);
		int itemId = createItemViaApi(managerCookie, projectId, statementId);

		mockMvc.perform(patch("/projects/{pid}/usage-statements/{sid}/items/{iid}", projectId, statementId, itemId)
						.cookie(managerCookie)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of(
								"usedOn", "2026-06-01",
								"itemName", "안전모 구입(수정)",
								"unit", "EA",
								"quantity", 20,
								"unitPrice", 6000,
								"totalAmount", 120000,
								"remark", "추가 구매",
								"pageNo", 2
						))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.itemName").value("안전모 구입(수정)"))
				.andExpect(jsonPath("$.data.quantity").value(20))
				.andExpect(jsonPath("$.data.totalAmount").value(120000))
				.andExpect(jsonPath("$.data.remark").value("추가 구매"))
				.andExpect(jsonPath("$.data.pageNo").value(2));
	}

	@Test
	void 존재하지_않는_세부항목_수정을_거부한다() throws Exception {
		Cookie managerCookie = loginCookie(createUser("admin"));
		int projectId = createProject(managerCookie);
		int statementId = insertUsageStatement(projectId);

		mockMvc.perform(patch("/projects/{pid}/usage-statements/{sid}/items/{iid}", projectId, statementId, 999_999_999L)
						.cookie(managerCookie)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of(
								"usedOn", "2026-05-01",
								"itemName", "없는항목",
								"quantity", 1,
								"unitPrice", 1000,
								"totalAmount", 1000,
								"pageNo", 1
						))))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.message").value("세부항목을 찾을 수 없습니다."));
	}

	@Test
	void 다른_프로젝트의_세부항목은_수정할_수_없다() throws Exception {
		Cookie managerCookie = loginCookie(createUser("admin"));
		int projectId = createProject(managerCookie);
		int otherProjectId = createProject(managerCookie);
		int otherStatementId = insertUsageStatement(otherProjectId);
		int otherItemId = insertUsageStatementItem(otherStatementId, "CAT_01");

		mockMvc.perform(patch("/projects/{pid}/usage-statements/{sid}/items/{iid}", projectId, otherStatementId, otherItemId)
						.cookie(managerCookie)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of(
								"usedOn", "2026-05-01",
								"itemName", "크로스 프로젝트 수정 시도",
								"quantity", 1,
								"unitPrice", 1000,
								"totalAmount", 1000,
								"pageNo", 1
						))))
				.andExpect(status().isNotFound());
	}

	// ─── R-14: 세부항목 삭제 ────────────────────────────────────────────

	@Test
	void 세부항목을_삭제하면_연결된_링크와_요건도_함께_삭제된다() throws Exception {
		Cookie managerCookie = loginCookie(createUser("admin"));
		Map<String, String> user = createUser("user");
		int userId = readUserIdFromLogin(user);
		int projectId = createProject(managerCookie);
		assign(managerCookie, projectId, userId);
		int statementId = insertUsageStatement(projectId);
		int itemId = insertUsageStatementItem(statementId, "CAT_01");
		int fileId = insertProjectFile(projectId, userId);
		insertEvidenceFileLink(itemId, fileId, "transaction_statement");
		insertEvidenceRequirement(itemId, "transaction_statement");

		assertThat(countLinksForItem(itemId)).isEqualTo(1);
		assertThat(countRequirementsForItem(itemId)).isEqualTo(1);

		mockMvc.perform(delete("/projects/{pid}/usage-statements/{sid}/items/{iid}", projectId, statementId, itemId)
						.cookie(managerCookie))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true));

		assertThat(countLinksForItem(itemId)).isEqualTo(0);
		assertThat(countRequirementsForItem(itemId)).isEqualTo(0);
	}

	@Test
	void 존재하지_않는_세부항목_삭제를_거부한다() throws Exception {
		Cookie managerCookie = loginCookie(createUser("admin"));
		int projectId = createProject(managerCookie);
		int statementId = insertUsageStatement(projectId);

		mockMvc.perform(delete("/projects/{pid}/usage-statements/{sid}/items/{iid}", projectId, statementId, 999_999_999L)
						.cookie(managerCookie))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.message").value("세부항목을 찾을 수 없습니다."));
	}

	@Test
	void 담당자가_아닌_user는_세부항목을_삭제할_수_없다() throws Exception {
		Cookie managerCookie = loginCookie(createUser("admin"));
		Cookie outsiderCookie = loginCookie(createUser("user"));
		int projectId = createProject(managerCookie);
		int statementId = insertUsageStatement(projectId);
		int itemId = insertUsageStatementItem(statementId, "CAT_01");

		mockMvc.perform(delete("/projects/{pid}/usage-statements/{sid}/items/{iid}", projectId, statementId, itemId)
						.cookie(outsiderCookie))
				.andExpect(status().isForbidden());
	}

	// ─── R-15: 세부항목 카테고리 이동 ───────────────────────────────────

	@Test
	void 세부항목의_카테고리를_이동할_수_있다() throws Exception {
		Cookie managerCookie = loginCookie(createUser("admin"));
		int projectId = createProject(managerCookie);
		int statementId = insertUsageStatement(projectId);
		int itemId = createItemViaApi(managerCookie, projectId, statementId);

		mockMvc.perform(patch("/projects/{pid}/usage-statements/{sid}/items/{iid}/category", projectId, statementId, itemId)
						.cookie(managerCookie)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of("categoryCode", "CAT_02"))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.categoryCode").value("CAT_02"));
	}

	@Test
	void 존재하지_않는_카테고리로_이동을_거부한다() throws Exception {
		Cookie managerCookie = loginCookie(createUser("admin"));
		int projectId = createProject(managerCookie);
		int statementId = insertUsageStatement(projectId);
		int itemId = createItemViaApi(managerCookie, projectId, statementId);

		mockMvc.perform(patch("/projects/{pid}/usage-statements/{sid}/items/{iid}/category", projectId, statementId, itemId)
						.cookie(managerCookie)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of("categoryCode", "INVALID_CAT"))))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("존재하지 않는 카테고리 코드입니다."));
	}

	@Test
	void 카테고리_이동_시_빈_코드를_거부한다() throws Exception {
		Cookie managerCookie = loginCookie(createUser("admin"));
		int projectId = createProject(managerCookie);
		int statementId = insertUsageStatement(projectId);
		int itemId = createItemViaApi(managerCookie, projectId, statementId);

		mockMvc.perform(patch("/projects/{pid}/usage-statements/{sid}/items/{iid}/category", projectId, statementId, itemId)
						.cookie(managerCookie)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of("categoryCode", ""))))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value(containsString("categoryCode:")));
	}

	// ─── D-05: statusCode 조회 응답 반영 ────────────────────────────────

	@Test
	void 사용내역서_목록_조회_시_statusCode가_포함된다() throws Exception {
		Cookie managerCookie = loginCookie(createUser("admin"));
		int projectId = createProject(managerCookie);
		insertUsageStatement(projectId);

		mockMvc.perform(get("/projects/{pid}/usage-statements", projectId)
						.cookie(managerCookie))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items[0].statusCode").value("draft"));
	}

	@Test
	void 사용내역서_상세_조회_시_statusCode가_포함된다() throws Exception {
		Cookie managerCookie = loginCookie(createUser("admin"));
		int projectId = createProject(managerCookie);
		int statementId = insertUsageStatement(projectId);

		mockMvc.perform(get("/projects/{pid}/usage-statements/{sid}", projectId, statementId)
						.cookie(managerCookie))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.statement.statusCode").value("draft"));
	}

	// ─── fixtures ────────────────────────────────────────────────────────

	private Map<String, String> createUser(String roleCode) {
		Map<String, String> request = Map.of(
				"employeeNo", "EMP-" + UUID.randomUUID(),
				"realName", "홍길동",
				"password", "P@ssw0rd123!",
				"roleCode", roleCode
		);
		userRepository.saveAndFlush(User.create(
				request.get("employeeNo"),
				request.get("realName"),
				passwordEncoder.encode(request.get("password")),
				RoleCode.from(roleCode)
		));
		return request;
	}

	private Cookie loginCookie(Map<String, String> user) throws Exception {
		MvcResult result = mockMvc.perform(post("/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of(
								"employeeNo", user.get("employeeNo"),
								"password", user.get("password")
						))))
				.andExpect(status().isOk())
				.andReturn();
		return result.getResponse().getCookie("access_token");
	}

	private int readUserIdFromLogin(Map<String, String> user) throws Exception {
		MvcResult result = mockMvc.perform(post("/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of(
								"employeeNo", user.get("employeeNo"),
								"password", user.get("password")
						))))
				.andExpect(status().isOk())
				.andReturn();
		return objectMapper.readTree(result.getResponse().getContentAsString())
				.path("data").path("user").path("id").asInt();
	}

	private int createProject(Cookie managerCookie) throws Exception {
		MvcResult result = mockMvc.perform(post("/projects")
						.cookie(managerCookie)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of(
								"contractNo", "CN-" + UUID.randomUUID(),
								"constructionCompany", "스칼라건설",
								"projectName", "테스트 프로젝트",
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

	private void assign(Cookie managerCookie, int projectId, int userId) throws Exception {
		mockMvc.perform(post("/projects/{pid}/assignees/{uid}", projectId, userId)
						.cookie(managerCookie))
				.andExpect(status().isOk());
	}

	private int insertUsageStatement(int projectId) {
		return jdbcTemplate.queryForObject("""
				INSERT INTO service.usage_statements
					(project_id, report_month, revision_no, document_written_date, cumulative_progress_rate)
				VALUES (?, '2026-05-01'::date, 1, '2026-05-15'::date, 30)
				RETURNING id
				""", Integer.class, projectId);
	}

	private int insertUsageStatementItem(int usageStatementId, String categoryCode) {
		return jdbcTemplate.queryForObject("""
				INSERT INTO service.usage_statement_items
					(usage_statement_id, category_code, used_on, item_name, quantity, unit_price, total_amount, page_no)
				VALUES (?, ?, '2026-05-01', '테스트 항목', 1, 1000, 1000, 1)
				RETURNING id
				""", Integer.class, usageStatementId, categoryCode);
	}

	private int insertProjectFile(int projectId, int uploadedByUserId) {
		return jdbcTemplate.queryForObject("""
				INSERT INTO service.files
					(project_id, uploaded_by_user_id, uploaded_evidence_type_code, original_filename, storage_key, mime_type, size_bytes)
				VALUES (?, ?, 'transaction_statement', 'receipt.pdf', ?, 'application/pdf', 1024)
				RETURNING id
				""", Integer.class, projectId, uploadedByUserId, "tests/" + UUID.randomUUID() + "/receipt.pdf");
	}

	private void insertEvidenceFileLink(int itemId, int fileId, String evidenceTypeCode) {
		jdbcTemplate.update("""
				INSERT INTO service.evidence_file_links
					(usage_statement_item_id, file_id, evidence_type_code)
				VALUES (?, ?, ?)
				""", itemId, fileId, evidenceTypeCode);
	}

	private void insertEvidenceRequirement(int itemId, String evidenceTypeCode) {
		jdbcTemplate.update("""
				INSERT INTO service.evidence_requirements
					(usage_statement_item_id, evidence_type_code, is_satisfied, is_active)
				VALUES (?, ?, false, true)
				""", itemId, evidenceTypeCode);
	}

	private int countLinksForItem(int itemId) {
		return jdbcTemplate.queryForObject(
				"SELECT count(*)::int FROM service.evidence_file_links WHERE usage_statement_item_id = ?",
				Integer.class, itemId);
	}

	private int countRequirementsForItem(int itemId) {
		return jdbcTemplate.queryForObject(
				"SELECT count(*)::int FROM service.evidence_requirements WHERE usage_statement_item_id = ?",
				Integer.class, itemId);
	}

	private Map<String, Object> defaultItemRequest() {
		return itemRequest("CAT_01", "안전모 구입", 10, 5000, 50000, 1);
	}

	private Map<String, Object> itemRequest(String categoryCode, String itemName, int quantity, int unitPrice, int totalAmount, int pageNo) {
		return Map.of(
				"categoryCode", categoryCode,
				"usedOn", "2026-05-01",
				"itemName", itemName,
				"unit", "개",
				"quantity", quantity,
				"unitPrice", unitPrice,
				"totalAmount", totalAmount,
				"pageNo", pageNo
		);
	}

	private int createItemViaApi(Cookie cookie, int projectId, int statementId) throws Exception {
		mockMvc.perform(post("/projects/{pid}/usage-statements/{sid}/items", projectId, statementId)
						.cookie(cookie)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(defaultItemRequest())))
				.andExpect(status().isCreated());
		return insertUsageStatementItem(statementId, "CAT_01");
	}
}
