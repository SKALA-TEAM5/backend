package com.skala.backend.project.controller;

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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProjectAssigneePermissionTest {

	@Autowired MockMvc mockMvc;
	@Autowired ObjectMapper objectMapper;
	@Autowired UserRepository userRepository;
	@Autowired PasswordEncoder passwordEncoder;

	@Test
	void 소속된_admin은_프로젝트_담당자를_추가하고_제거할_수_있다() throws Exception {
		Cookie ownerCookie = loginCookie(createUser("admin"));
		int targetUserId = createUserId("user");
		int projectId = createProject(ownerCookie, "소속_admin_추가제거");

		mockMvc.perform(post("/projects/{projectId}/assignees/{userId}", projectId, targetUserId)
						.cookie(ownerCookie))
				.andExpect(status().isOk());

		mockMvc.perform(delete("/projects/{projectId}/assignees/{userId}", projectId, targetUserId)
						.cookie(ownerCookie))
				.andExpect(status().isOk());
	}

	@Test
	void 소속되지_않은_admin은_담당자_추가를_할_수_없다() throws Exception {
		Cookie ownerCookie = loginCookie(createUser("admin"));
		Cookie outsiderCookie = loginCookie(createUser("admin"));
		int targetUserId = createUserId("user");
		int projectId = createProject(ownerCookie, "비소속_admin_추가차단");

		mockMvc.perform(post("/projects/{projectId}/assignees/{userId}", projectId, targetUserId)
						.cookie(outsiderCookie))
				.andExpect(status().isForbidden());
	}

	@Test
	void 소속되지_않은_admin은_담당자_제거를_할_수_없다() throws Exception {
		Cookie ownerCookie = loginCookie(createUser("admin"));
		Cookie outsiderCookie = loginCookie(createUser("admin"));
		int targetUserId = createUserId("user");
		int projectId = createProject(ownerCookie, "비소속_admin_제거차단");

		mockMvc.perform(post("/projects/{projectId}/assignees/{userId}", projectId, targetUserId)
						.cookie(ownerCookie))
				.andExpect(status().isOk());

		mockMvc.perform(delete("/projects/{projectId}/assignees/{userId}", projectId, targetUserId)
						.cookie(outsiderCookie))
				.andExpect(status().isForbidden());
	}

	@Test
	void 소속되지_않은_admin은_담당자_교체를_할_수_없다() throws Exception {
		Cookie ownerCookie = loginCookie(createUser("admin"));
		Cookie outsiderCookie = loginCookie(createUser("admin"));
		int targetUserId = createUserId("user");
		int projectId = createProject(ownerCookie, "비소속_admin_교체차단");

		mockMvc.perform(put("/projects/{projectId}/assignees", projectId)
						.cookie(outsiderCookie)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of("assigneeUserIds", List.of(targetUserId)))))
				.andExpect(status().isForbidden());
	}

	@Test
	void system_admin은_프로젝트_담당자로_추가할_수_없다() throws Exception {
		Cookie ownerCookie = loginCookie(createUser("admin"));
		int systemAdminId = createUserId("system_admin");
		int projectId = createProject(ownerCookie, "system_admin_추가차단");

		mockMvc.perform(post("/projects/{projectId}/assignees/{userId}", projectId, systemAdminId)
						.cookie(ownerCookie))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("system_admin은 프로젝트 담당자로 추가할 수 없습니다."));
	}

	@Test
	void system_admin을_포함한_담당자_교체는_거부된다() throws Exception {
		Cookie ownerCookie = loginCookie(createUser("admin"));
		int regularUserId = createUserId("user");
		int systemAdminId = createUserId("system_admin");
		int projectId = createProject(ownerCookie, "system_admin_교체차단");

		mockMvc.perform(put("/projects/{projectId}/assignees", projectId)
						.cookie(ownerCookie)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of("assigneeUserIds", List.of(regularUserId, systemAdminId)))))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("system_admin은 프로젝트 담당자로 추가할 수 없습니다."));
	}

	@Test
	void 소속된_admin은_프로젝트를_수정할_수_있다() throws Exception {
		Cookie ownerCookie = loginCookie(createUser("admin"));
		int projectId = createProject(ownerCookie, "소속_admin_수정");

		mockMvc.perform(patch("/projects/{projectId}", projectId)
						.cookie(ownerCookie)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of("projectName", "수정됨"))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.project.projectName").value("수정됨"));
	}

	@Test
	void 소속되지_않은_admin은_프로젝트를_수정할_수_없다() throws Exception {
		Cookie ownerCookie = loginCookie(createUser("admin"));
		Cookie outsiderCookie = loginCookie(createUser("admin"));
		int projectId = createProject(ownerCookie, "비소속_admin_수정차단");

		mockMvc.perform(patch("/projects/{projectId}", projectId)
						.cookie(outsiderCookie)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of("projectName", "침범시도"))))
				.andExpect(status().isForbidden());
	}

	@Test
	void 소속된_admin은_프로젝트를_삭제할_수_있다() throws Exception {
		Cookie ownerCookie = loginCookie(createUser("admin"));
		int projectId = createProject(ownerCookie, "소속_admin_삭제");

		mockMvc.perform(delete("/projects/{projectId}", projectId)
						.cookie(ownerCookie))
				.andExpect(status().isOk());
	}

	@Test
	void 소속되지_않은_admin은_프로젝트를_삭제할_수_없다() throws Exception {
		Cookie ownerCookie = loginCookie(createUser("admin"));
		Cookie outsiderCookie = loginCookie(createUser("admin"));
		int projectId = createProject(ownerCookie, "비소속_admin_삭제차단");

		mockMvc.perform(delete("/projects/{projectId}", projectId)
						.cookie(outsiderCookie))
				.andExpect(status().isForbidden());
	}

	@Test
	void agent는_소속_여부와_무관하게_담당자를_관리할_수_있다() throws Exception {
		Cookie ownerCookie = loginCookie(createUser("admin"));
		Cookie agentCookie = loginCookie(createUser("agent"));
		int targetUserId = createUserId("user");
		int projectId = createProject(ownerCookie, "agent_소속무관_관리");

		mockMvc.perform(post("/projects/{projectId}/assignees/{userId}", projectId, targetUserId)
						.cookie(agentCookie))
				.andExpect(status().isOk());

		mockMvc.perform(delete("/projects/{projectId}/assignees/{userId}", projectId, targetUserId)
						.cookie(agentCookie))
				.andExpect(status().isOk());
	}

	@Test
	void 마지막_admin은_프로젝트에서_제거할_수_없다() throws Exception {
		Cookie ownerCookie = loginCookie(createUser("admin"));
		int ownerId = parseUserId(ownerCookie);
		int projectId = createProject(ownerCookie, "마지막admin_제거차단");

		mockMvc.perform(delete("/projects/{projectId}/assignees/{userId}", projectId, ownerId)
						.cookie(ownerCookie))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value("프로젝트에 최소 한 명의 admin이 있어야 합니다."));
	}

	@Test
	void admin이_두_명일_때_한_명_제거는_가능하다() throws Exception {
		Cookie ownerCookie = loginCookie(createUser("admin"));
		int ownerId = parseUserId(ownerCookie);
		int secondAdminId = createUserId("admin");
		int projectId = createProject(ownerCookie, "admin두명_한명제거");

		Cookie agentCookie = loginCookie(createUser("agent"));
		mockMvc.perform(post("/projects/{projectId}/assignees/{userId}", projectId, secondAdminId)
						.cookie(agentCookie))
				.andExpect(status().isOk());

		// secondAdmin 제거 — owner(admin)가 남으므로 가능
		mockMvc.perform(delete("/projects/{projectId}/assignees/{userId}", projectId, secondAdminId)
						.cookie(ownerCookie))
				.andExpect(status().isOk());
	}

	@Test
	void agent는_admin_없는_목록으로_교체할_수_없다() throws Exception {
		Cookie ownerCookie = loginCookie(createUser("admin"));
		Cookie agentCookie = loginCookie(createUser("agent"));
		int targetUserId = createUserId("user");
		int projectId = createProject(ownerCookie, "agent_admin없는교체차단");

		mockMvc.perform(put("/projects/{projectId}/assignees", projectId)
						.cookie(agentCookie)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of("assigneeUserIds", List.of(targetUserId)))))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value("프로젝트에 최소 한 명의 admin이 있어야 합니다."));
	}

	@Test
	void agent는_admin_포함_목록으로_교체할_수_있다() throws Exception {
		Cookie ownerCookie = loginCookie(createUser("admin"));
		Cookie agentCookie = loginCookie(createUser("agent"));
		int ownerId = parseUserId(ownerCookie);
		int targetUserId = createUserId("user");
		int projectId = createProject(ownerCookie, "agent_admin포함교체_허용");

		mockMvc.perform(put("/projects/{projectId}/assignees", projectId)
						.cookie(agentCookie)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of("assigneeUserIds", List.of(ownerId, targetUserId)))))
				.andExpect(status().isOk());
	}

	@Test
	void 여러_프로젝트에_소속된_admin은_각_프로젝트_담당자를_개별_관리할_수_있다() throws Exception {
		Cookie adminCookie = loginCookie(createUser("admin"));
		Cookie otherAdminCookie = loginCookie(createUser("admin"));
		int targetUserId = createUserId("user");

		int projectA = createProject(adminCookie, "다중소속_A");
		int projectB = createProject(otherAdminCookie, "다중소속_B");

		// admin을 projectB에도 추가 (agent를 통해)
		Cookie agentCookie = loginCookie(createUser("agent"));
		mockMvc.perform(post("/projects/{projectId}/assignees/{userId}", projectB,
						parseUserId(adminCookie))
						.cookie(agentCookie))
				.andExpect(status().isOk());

		// admin은 이제 A, B 둘 다 관리 가능
		mockMvc.perform(post("/projects/{projectId}/assignees/{userId}", projectA, targetUserId)
						.cookie(adminCookie))
				.andExpect(status().isOk());

		mockMvc.perform(post("/projects/{projectId}/assignees/{userId}", projectB, targetUserId)
						.cookie(adminCookie))
				.andExpect(status().isOk());
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

	private int createUserId(String roleCode) throws Exception {
		Map<String, String> user = createUser(roleCode);
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

	private int parseUserId(Cookie cookie) throws Exception {
		MvcResult result = mockMvc.perform(
						org.springframework.test.web.servlet.request.MockMvcRequestBuilders
								.get("/users/me").cookie(cookie))
				.andExpect(status().isOk())
				.andReturn();
		return objectMapper.readTree(result.getResponse().getContentAsString())
				.path("data").path("user").path("id").asInt();
	}

	private int createProject(Cookie ownerCookie, String projectName) throws Exception {
		MvcResult result = mockMvc.perform(post("/projects")
						.cookie(ownerCookie)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of(
								"contractNo", "CN-" + UUID.randomUUID(),
								"constructionCompany", "스칼라건설",
								"projectName", projectName,
								"siteLocation", "서울시 강남구",
								"contractAmount", 100000000,
								"constructionStartDate", "2026-05-01",
								"constructionEndDate", "2026-12-31",
								"appropriatedAmount", 10000000,
								"status", "active"
						))))
				.andExpect(status().isCreated())
				.andReturn();
		return objectMapper.readTree(result.getResponse().getContentAsString())
				.path("data").path("project").path("id").asInt();
	}
}
