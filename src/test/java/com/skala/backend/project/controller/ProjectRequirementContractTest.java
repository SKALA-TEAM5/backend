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
	void admin은_프로젝트를_CRUD_할_수_있다() throws Exception {
		Cookie adminCookie = loginCookie(createUser("admin"));

		MvcResult createResult = mockMvc.perform(post("/projects")
						.cookie(adminCookie)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(projectRequest("관리자 생성 프로젝트"))))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.project.projectName").value("관리자 생성 프로젝트"))
				.andReturn();

		int projectId = readId(createResult, "project");

		mockMvc.perform(get("/projects/{projectId}", projectId).cookie(adminCookie))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.project.id").value(projectId));

		mockMvc.perform(patch("/projects/{projectId}", projectId)
						.cookie(adminCookie)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of("projectName", "관리자 수정 프로젝트"))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.project.projectName").value("관리자 수정 프로젝트"));

		mockMvc.perform(delete("/projects/{projectId}", projectId).cookie(adminCookie))
				.andExpect(status().isOk());

		mockMvc.perform(get("/projects/{projectId}", projectId).cookie(adminCookie))
				.andExpect(status().isNotFound());
	}

	@Test
	void admin은_프로젝트와_유저를_다대다로_연결할_수_있다() throws Exception {
		Cookie adminCookie = loginCookie(createUser("admin"));
		int firstUserId = createUserByAdmin(adminCookie, "user");
		int secondUserId = createUserByAdmin(adminCookie, "user");
		int projectId = createProject(adminCookie, "담당자 연결 프로젝트");

		mockMvc.perform(put("/projects/{projectId}/assignees", projectId)
						.cookie(adminCookie)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of("assigneeUserIds", List.of(firstUserId, secondUserId)))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.assignees", hasSize(2)));

		mockMvc.perform(delete("/projects/{projectId}/assignees/{userId}", projectId, secondUserId)
						.cookie(adminCookie))
				.andExpect(status().isOk());

		mockMvc.perform(get("/projects/{projectId}/assignees", projectId)
						.cookie(adminCookie))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.assignees", hasSize(1)))
				.andExpect(jsonPath("$.data.assignees[0].userId").value(firstUserId));
	}

	@Test
	void 일반_user는_본인이_담당한_프로젝트만_조회할_수_있고_수정은_할_수_없다() throws Exception {
		Cookie adminCookie = loginCookie(createUser("admin"));
		Map<String, String> assignee = createUser("user");
		Map<String, String> outsider = createUser("user");
		Cookie assigneeCookie = loginCookie(assignee);
		Cookie outsiderCookie = loginCookie(outsider);
		int assigneeId = readUserIdFromLogin(assignee);
		int assignedProjectId = createProject(adminCookie, "담당 프로젝트");
		int unassignedProjectId = createProject(adminCookie, "미담당 프로젝트");

		mockMvc.perform(post("/projects/{projectId}/assignees/{userId}", assignedProjectId, assigneeId)
						.cookie(adminCookie))
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
						.content(objectMapper.writeValueAsString(Map.of("projectName", "담당자가 수정한 프로젝트"))))
				.andExpect(status().isForbidden());

		mockMvc.perform(post("/projects")
						.cookie(assigneeCookie)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(projectRequest("일반 사용자 생성 시도"))))
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
						.content(objectMapper.writeValueAsString(Map.of("projectName", "외부 사용자 수정 시도"))))
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

	private int createProject(Cookie adminCookie, String projectName) throws Exception {
		MvcResult result = mockMvc.perform(post("/projects")
						.cookie(adminCookie)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(projectRequest(projectName))))
				.andExpect(status().isCreated())
				.andReturn();

		return readId(result, "project");
	}

	private int createUserByAdmin(Cookie adminCookie, String roleCode) throws Exception {
		MvcResult result = mockMvc.perform(post("/users")
						.cookie(adminCookie)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of(
								"employeeNo", "EMP-" + UUID.randomUUID(),
								"realName", "프로젝트담당자",
								"password", "P@ssw0rd123!",
								"roleCode", roleCode
						))))
				.andExpect(status().isCreated())
				.andReturn();

		return readId(result, "user");
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
