package com.skala.backend.aiagent.controller;

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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("mock-aiagent")
@Transactional
class AiAgentRunContractTest {

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

	@Test
	void admin은_파일_context로_ai_agent_run을_실행하고_validation_logs를_저장한다() throws Exception {
		Map<String, String> admin = createUser("admin");
		Cookie adminCookie = loginCookie(admin);
		int adminId = readUserIdFromLogin(admin);
		int projectId = insertProject();
		int fileId = insertProjectFile(projectId, adminId);

		mockMvc.perform(post("/projects/{projectId}/ai-agent-runs", projectId)
						.cookie(adminCookie)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of(
								"agentTypeCode", "ocr-agent",
								"fileIds", java.util.List.of(fileId)
						))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.projectId").value(projectId))
				.andExpect(jsonPath("$.data.agentTypeCode").value("ocr-agent"))
				.andExpect(jsonPath("$.data.statusCode").value("completed"))
				.andExpect(jsonPath("$.data.resultCount").value(1));

		Map<String, Object> run = jdbcTemplate.queryForMap("""
				SELECT id, requested_by_user_id, agent_type_code, status_code
				FROM service.ai_agent_runs
				WHERE project_id = ?
				""", projectId);
		assertThat(run.get("requested_by_user_id")).isEqualTo((long) adminId);
		assertThat(run.get("agent_type_code")).isEqualTo("ocr-agent");
		assertThat(run.get("status_code")).isEqualTo("completed");

		Map<String, Object> log = jdbcTemplate.queryForMap("""
				SELECT ai_agent_run_id, agent_type_code, validation_type_code, log_type_code, severity_code, result_code
				FROM service.validation_logs
				WHERE project_id = ?
				""", projectId);
		assertThat(log.get("ai_agent_run_id")).isEqualTo(run.get("id"));
		assertThat(log.get("agent_type_code")).isEqualTo("ocr-agent");
		assertThat(log.get("validation_type_code")).isEqualTo("ocr");
		assertThat(log.get("log_type_code")).isEqualTo("agent_mock_result");
		assertThat(log.get("severity_code")).isEqualTo("info");
		assertThat(log.get("result_code")).isEqualTo("pass");
	}

	@Test
	void 권한없는_user는_ai_agent_run을_실행할_수_없다() throws Exception {
		Cookie userCookie = loginCookie(createUser("user"));
		int projectId = insertProject();

		mockMvc.perform(post("/projects/{projectId}/ai-agent-runs", projectId)
						.cookie(userCookie)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of("agentTypeCode", "validator-agent"))))
				.andExpect(status().isForbidden());
	}

	@Test
	void 다른_프로젝트_파일을_넘기면_ai_agent_run을_거부한다() throws Exception {
		Map<String, String> admin = createUser("admin");
		Cookie adminCookie = loginCookie(admin);
		int adminId = readUserIdFromLogin(admin);
		int projectId = insertProject();
		int otherProjectId = insertProject();
		int otherProjectFileId = insertProjectFile(otherProjectId, adminId);

		mockMvc.perform(post("/projects/{projectId}/ai-agent-runs", projectId)
						.cookie(adminCookie)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of(
								"agentTypeCode", "ocr-agent",
								"fileIds", java.util.List.of(otherProjectFileId)
						))))
				.andExpect(status().isBadRequest());
	}

	@Test
	void 같은_프로젝트의_같은_agent가_running이면_중복실행을_거부한다() throws Exception {
		Map<String, String> admin = createUser("admin");
		Cookie adminCookie = loginCookie(admin);
		int adminId = readUserIdFromLogin(admin);
		int projectId = insertProject();
		insertRunningAgentRun(projectId, adminId, "validator-agent");

		mockMvc.perform(post("/projects/{projectId}/ai-agent-runs", projectId)
						.cookie(adminCookie)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of("agentTypeCode", "validator-agent"))))
				.andExpect(status().isConflict());
	}

	@Test
	void 지원하지_않는_agentTypeCode는_거부한다() throws Exception {
		Cookie adminCookie = loginCookie(createUser("admin"));
		int projectId = insertProject();

		mockMvc.perform(post("/projects/{projectId}/ai-agent-runs", projectId)
						.cookie(adminCookie)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"agentTypeCode":"unknown-agent"}
								"""))
				.andExpect(status().isBadRequest());
	}

	private int insertProject() {
		String suffix = UUID.randomUUID().toString();
		return jdbcTemplate.queryForObject("""
				INSERT INTO service.projects
					(contract_no, construction_company, project_name, site_location,
					 contract_amount, construction_start_date, construction_end_date, appropriated_amount)
				VALUES (?, '테스트 건설', 'AI Agent 테스트 프로젝트', '서울시 강남구',
					100000000, '2026-01-01', '2026-12-31', 10000000)
				RETURNING id
				""", Integer.class, "AI-" + suffix);
	}

	private int insertProjectFile(int projectId, int uploadedByUserId) {
		return jdbcTemplate.queryForObject("""
				INSERT INTO service.files
					(project_id, uploaded_by_user_id, uploaded_evidence_type_code, original_filename, storage_key, mime_type, size_bytes)
				VALUES (?, ?, 'receipt', 'receipt.pdf', ?, 'application/pdf', 1024)
				RETURNING id
				""", Integer.class, projectId, uploadedByUserId, "tests/" + UUID.randomUUID() + "/receipt.pdf");
	}

	private void insertRunningAgentRun(int projectId, int requestedByUserId, String agentTypeCode) {
		jdbcTemplate.update("""
				INSERT INTO service.ai_agent_runs
					(project_id, requested_by_user_id, agent_type_code, status_code, started_at)
				VALUES (?, ?, ?, 'running', now())
				""", projectId, requestedByUserId, agentTypeCode);
	}

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

	private Cookie loginCookie(Map<String, String> signupRequest) throws Exception {
		MvcResult result = mockMvc.perform(post("/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of(
								"employeeNo", signupRequest.get("employeeNo"),
								"password", signupRequest.get("password")
						))))
				.andExpect(status().isOk())
				.andReturn();

		return result.getResponse().getCookie("access_token");
	}

	private int readUserIdFromLogin(Map<String, String> signupRequest) throws Exception {
		MvcResult result = mockMvc.perform(post("/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of(
								"employeeNo", signupRequest.get("employeeNo"),
								"password", signupRequest.get("password")
						))))
				.andExpect(status().isOk())
				.andReturn();

		return objectMapper.readTree(result.getResponse().getContentAsString())
				.path("data")
				.path("user")
				.path("id")
				.asInt();
	}
}
