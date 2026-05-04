package com.skala.backend.project.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProjectRequirementContractTest {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	ObjectMapper objectMapper;

	@Test
	void hq는_프로젝트를_CRUD_할_수_있다() throws Exception {
		Cookie managerCookie = loginCookie(createUser("hq"));

		MvcResult createResult = mockMvc.perform(post("/projects")
						.cookie(managerCookie)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(projectRequest("hq 생성 프로젝트"))))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.project.projectName").value("hq 생성 프로젝트"))
				.andReturn();

		int projectId = readId(createResult, "project");

		mockMvc.perform(get("/projects/{projectId}", projectId).cookie(managerCookie))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.project.id").value(projectId));

		mockMvc.perform(patch("/projects/{projectId}", projectId)
						.cookie(managerCookie)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of("projectName", "hq 수정 프로젝트"))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.project.projectName").value("hq 수정 프로젝트"));

		mockMvc.perform(delete("/projects/{projectId}", projectId).cookie(managerCookie))
				.andExpect(status().isOk());

		mockMvc.perform(get("/projects/{projectId}", projectId).cookie(managerCookie))
				.andExpect(status().isNotFound());
	}

	@Test
	void 시스템_admin은_프로젝트_업무_API를_수행할_수_없다() throws Exception {
		Cookie adminCookie = loginCookie(createUser("admin"));
		Cookie managerCookie = loginCookie(createUser("hq"));
		int siteUserId = createUserId("site");
		int projectId = createProject(managerCookie, "hq 담당 프로젝트");

		mockMvc.perform(post("/projects")
						.cookie(adminCookie)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(projectRequest("시스템 admin 생성 시도"))))
				.andExpect(status().isForbidden());

		mockMvc.perform(get("/projects/{projectId}", projectId).cookie(adminCookie))
				.andExpect(status().isForbidden());

		mockMvc.perform(post("/projects/{projectId}/assignees/{userId}", projectId, siteUserId)
						.cookie(adminCookie))
				.andExpect(status().isForbidden());
	}

	@Test
	void hq는_프로젝트의_site_담당자를_배정_교체_해제할_수_있다() throws Exception {
		Cookie managerCookie = loginCookie(createUser("hq"));
		int firstUserId = createUserId("site");
		int secondUserId = createUserId("site");
		int replacementUserId = createUserId("site");
		int projectId = createProject(managerCookie, "담당자 연결 프로젝트");

		mockMvc.perform(put("/projects/{projectId}/assignees", projectId)
						.cookie(managerCookie)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of("assigneeUserIds", List.of(firstUserId, secondUserId)))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.assignees", hasSize(2)))
				.andExpect(jsonPath("$.data.assignees[0].userId").value(firstUserId))
				.andExpect(jsonPath("$.data.assignees[0].roleCode").value("site"))
				.andExpect(jsonPath("$.data.assignees[1].userId").value(secondUserId))
				.andExpect(jsonPath("$.data.assignees[1].roleCode").value("site"));

		mockMvc.perform(get("/projects")
						.cookie(managerCookie)
						.param("assigneeUserId", String.valueOf(firstUserId)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items", hasSize(1)))
				.andExpect(jsonPath("$.data.items[0].id").value(projectId));

		mockMvc.perform(put("/projects/{projectId}/assignees", projectId)
						.cookie(managerCookie)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of("assigneeUserIds", List.of(replacementUserId)))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.assignees", hasSize(1)))
				.andExpect(jsonPath("$.data.assignees[0].userId").value(replacementUserId))
				.andExpect(jsonPath("$.data.assignees[0].roleCode").value("site"));

		mockMvc.perform(get("/projects/{projectId}", projectId)
						.cookie(managerCookie))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.project.assignees", hasSize(1)))
				.andExpect(jsonPath("$.data.project.assignees[0].userId").value(replacementUserId));

		mockMvc.perform(delete("/projects/{projectId}/assignees/{userId}", projectId, replacementUserId)
						.cookie(managerCookie))
				.andExpect(status().isOk());

		mockMvc.perform(get("/projects/{projectId}/assignees", projectId)
						.cookie(managerCookie))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.assignees", hasSize(0)));
	}

	@Test
	void agent는_hq와_동일하게_프로젝트를_관리할_수_있다() throws Exception {
		Cookie agentCookie = loginCookie(createUser("agent"));
		int siteUserId = createUserId("site");
		int projectId = createProject(agentCookie, "agent 생성 프로젝트");

		mockMvc.perform(post("/projects/{projectId}/assignees/{userId}", projectId, siteUserId)
						.cookie(agentCookie))
				.andExpect(status().isOk());

		mockMvc.perform(get("/projects")
						.cookie(agentCookie)
						.param("assigneeUserId", String.valueOf(siteUserId)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items", hasSize(1)))
				.andExpect(jsonPath("$.data.items[0].id").value(projectId));
	}

	@Test
	void site는_본인이_담당한_프로젝트만_조회할_수_있고_수정은_할_수_없다() throws Exception {
		Cookie managerCookie = loginCookie(createUser("hq"));
		Map<String, String> assignee = createUser("site");
		Map<String, String> outsider = createUser("site");
		Cookie assigneeCookie = loginCookie(assignee);
		Cookie outsiderCookie = loginCookie(outsider);
		int assigneeId = readUserIdFromLogin(assignee);
		int assignedProjectId = createProject(managerCookie, "담당 프로젝트");
		int unassignedProjectId = createProject(managerCookie, "미담당 프로젝트");

		mockMvc.perform(post("/projects/{projectId}/assignees/{userId}", assignedProjectId, assigneeId)
						.cookie(managerCookie))
				.andExpect(status().isOk());

		mockMvc.perform(get("/projects/{projectId}", assignedProjectId).cookie(assigneeCookie))
				.andExpect(status().isOk());

		mockMvc.perform(get("/projects")
						.cookie(assigneeCookie)
						.param("keyword", "담당 프로젝트"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items", hasSize(1)))
				.andExpect(jsonPath("$.data.items[0].id").value(assignedProjectId));

		mockMvc.perform(get("/projects/{projectId}/assignees", assignedProjectId).cookie(assigneeCookie))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.assignees", hasSize(1)));

		mockMvc.perform(patch("/projects/{projectId}", assignedProjectId)
						.cookie(assigneeCookie)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of("projectName", "site가 수정한 프로젝트"))))
				.andExpect(status().isForbidden());

		mockMvc.perform(post("/projects")
						.cookie(assigneeCookie)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(projectRequest("site 생성 시도"))))
				.andExpect(status().isForbidden());

		mockMvc.perform(delete("/projects/{projectId}", assignedProjectId)
						.cookie(assigneeCookie))
				.andExpect(status().isForbidden());

		mockMvc.perform(put("/projects/{projectId}/assignees", assignedProjectId)
						.cookie(assigneeCookie)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of("assigneeUserIds", List.of(assigneeId)))))
				.andExpect(status().isForbidden());

		mockMvc.perform(post("/projects/{projectId}/assignees/{userId}", assignedProjectId, assigneeId)
						.cookie(assigneeCookie))
				.andExpect(status().isForbidden());

		mockMvc.perform(get("/projects/{projectId}", unassignedProjectId).cookie(assigneeCookie))
				.andExpect(status().isForbidden());

		mockMvc.perform(patch("/projects/{projectId}", assignedProjectId)
						.cookie(outsiderCookie)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of("projectName", "미배정 site 수정 시도"))))
				.andExpect(status().isForbidden());
	}

	private Map<String, Object> projectRequest(String projectName) {
		return Map.of(
				"contractNo", "CN-" + UUID.randomUUID(),
				"constructionCompany", "스칼라건설",
				"projectName", projectName,
				"siteLocation", "서울시 강남구",
				"contractAmount", 100000000,
				"constructionStartDate", "2026-05-01",
				"constructionEndDate", "2026-12-31",
				"appropriatedAmount", 10000000
		);
	}

	private int createProject(Cookie managerCookie, String projectName) throws Exception {
		MvcResult result = mockMvc.perform(post("/projects")
						.cookie(managerCookie)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(projectRequest(projectName))))
				.andExpect(status().isCreated())
				.andReturn();

		return readId(result, "project");
	}

	private int createUserId(String roleCode) throws Exception {
		return readUserIdFromLogin(createUser(roleCode));
	}

	private Map<String, String> createUser(String roleCode) throws Exception {
		Map<String, String> request = Map.of(
				"employeeNo", "EMP-" + UUID.randomUUID(),
				"realName", "홍길동",
				"password", "P@ssw0rd123!",
				"roleCode", roleCode
		);

		mockMvc.perform(post("/auth/signup")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isCreated());

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

		return readId(result, "user");
	}

	private int readId(MvcResult result, String nodeName) throws Exception {
		return objectMapper.readTree(result.getResponse().getContentAsString())
				.path("data")
				.path(nodeName)
				.path("id")
				.asInt();
	}
}
