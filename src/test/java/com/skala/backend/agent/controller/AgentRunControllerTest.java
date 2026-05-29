package com.skala.backend.agent.controller;

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
import org.springframework.web.client.RestClientException;

import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
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

	@Test
	void admin이_parse를_호출하면_FastAPI가_호출되고_200을_반환한다() throws Exception {
		Cookie cookie = loginCookie(createUser("admin"));
		int projectId = createProject(cookie);

		mockMvc.perform(post("/projects/{pid}/agents/parse", projectId)
						.cookie(cookie)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of("fileId", 10))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true));

		verify(fastApiAgentClient).parseUsageStatement(10L);
	}

	@Test
	void parse_요청에_fileId가_없으면_400을_반환한다() throws Exception {
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
	void 담당자가_아닌_user는_parse를_호출할_수_없다() throws Exception {
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
	void FastAPI_장애_시_parse는_503을_반환한다() throws Exception {
		Cookie cookie = loginCookie(createUser("admin"));
		int projectId = createProject(cookie);

		doThrow(new RestClientException("FastAPI unavailable"))
				.when(fastApiAgentClient).parseUsageStatement(anyLong());

		mockMvc.perform(post("/projects/{pid}/agents/parse", projectId)
						.cookie(cookie)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of("fileId", 10))))
				.andExpect(status().isServiceUnavailable());
	}

	// ─── POST /agents/classify ────────────────────────────────────────────

	@Test
	void 담당자_user가_classify를_호출하면_FastAPI가_호출되고_200을_반환한다() throws Exception {
		Cookie adminCookie = loginCookie(createUser("admin"));
		Map<String, String> user = createUser("user");
		Cookie userCookie = loginCookie(user);
		int projectId = createProject(adminCookie);
		assign(adminCookie, projectId, readUserId(user));
		int statementId = insertStatement(projectId);

		mockMvc.perform(post("/projects/{pid}/agents/classify", projectId)
						.cookie(userCookie)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of(
								"usageStatementId", statementId,
								"itemName", "안전모",
								"usedOn", "2026-04-15",
								"unit", "개",
								"quantity", 10,
								"unitPrice", 15000,
								"totalAmount", 150000
						))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true));

		verify(fastApiAgentClient).classifyItem(anyLong(), anyLong(), any(), any(), any(), any(), any(), anyLong());
	}

	@Test
	void classify_요청에_itemName이_없으면_400을_반환한다() throws Exception {
		Cookie cookie = loginCookie(createUser("admin"));
		int projectId = createProject(cookie);

		mockMvc.perform(post("/projects/{pid}/agents/classify", projectId)
						.cookie(cookie)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of(
								"usageStatementId", 1,
								"usedOn", "2026-04-15",
								"quantity", 10,
								"unitPrice", 15000,
								"totalAmount", 150000
						))))
				.andExpect(status().isBadRequest());
	}

	@Test
	void classify_미인증_요청은_401을_반환한다() throws Exception {
		Cookie cookie = loginCookie(createUser("admin"));
		int projectId = createProject(cookie);

		mockMvc.perform(post("/projects/{pid}/agents/classify", projectId)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of(
								"usageStatementId", 1,
								"itemName", "안전모",
								"usedOn", "2026-04-15",
								"quantity", 10,
								"unitPrice", 15000,
								"totalAmount", 150000
						))))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void FastAPI_장애_시_classify는_503을_반환한다() throws Exception {
		Cookie adminCookie = loginCookie(createUser("admin"));
		Map<String, String> user = createUser("user");
		Cookie userCookie = loginCookie(user);
		int projectId = createProject(adminCookie);
		assign(adminCookie, projectId, readUserId(user));
		int statementId = insertStatement(projectId);

		doThrow(new RestClientException("FastAPI unavailable"))
				.when(fastApiAgentClient).classifyItem(anyLong(), anyLong(), any(), any(), any(), any(), any(), anyLong());

		mockMvc.perform(post("/projects/{pid}/agents/classify", projectId)
						.cookie(userCookie)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of(
								"usageStatementId", statementId,
								"itemName", "안전모",
								"usedOn", "2026-04-15",
								"quantity", 10,
								"unitPrice", 15000,
								"totalAmount", 150000
						))))
				.andExpect(status().isServiceUnavailable());
	}

	// ─── POST /agents/validate ────────────────────────────────────────────

	@Test
	void admin이_validate를_호출하면_FastAPI가_호출되고_200을_반환한다() throws Exception {
		Cookie cookie = loginCookie(createUser("admin"));
		int projectId = createProject(cookie);
		int statementId = insertStatement(projectId);

		mockMvc.perform(post("/projects/{pid}/agents/validate", projectId)
						.cookie(cookie)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of("usageStatementId", statementId))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true));

		verify(fastApiAgentClient).runValidation(anyLong(), anyLong());
	}

	@Test
	void validate_요청에_usageStatementId가_없으면_400을_반환한다() throws Exception {
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
	void FastAPI_장애_시_validate는_503을_반환한다() throws Exception {
		Cookie cookie = loginCookie(createUser("admin"));
		int projectId = createProject(cookie);
		int statementId = insertStatement(projectId);

		doThrow(new RestClientException("FastAPI unavailable"))
				.when(fastApiAgentClient).runValidation(anyLong(), anyLong());

		mockMvc.perform(post("/projects/{pid}/agents/validate", projectId)
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
								"appropriatedAmount", 10_000_000
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
		return jdbcTemplate.queryForObject("""
				INSERT INTO service.usage_statements
					(project_id, report_month, revision_no, document_written_date, cumulative_progress_rate)
				VALUES (?, '2026-05-01', 1, '2026-05-01', 30)
				RETURNING id
				""", Integer.class, projectId);
	}
}
