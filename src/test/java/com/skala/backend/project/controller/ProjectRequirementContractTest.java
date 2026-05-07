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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
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

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Autowired
	UserRepository userRepository;

	@Autowired
	PasswordEncoder passwordEncoder;

	@Test
	void admin은_프로젝트를_CRUD_할_수_있다() throws Exception {
		Cookie managerCookie = loginCookie(createUser("admin"));

		MvcResult createResult = mockMvc.perform(post("/projects")
						.cookie(managerCookie)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(projectRequest("admin 생성 프로젝트"))))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.project.projectName").value("admin 생성 프로젝트"))
				.andReturn();

		int projectId = readId(createResult, "project");

		mockMvc.perform(get("/projects/{projectId}", projectId).cookie(managerCookie))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.project.id").value(projectId));

		mockMvc.perform(patch("/projects/{projectId}", projectId)
						.cookie(managerCookie)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of("projectName", "admin 수정 프로젝트"))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.project.projectName").value("admin 수정 프로젝트"));

		mockMvc.perform(delete("/projects/{projectId}", projectId).cookie(managerCookie))
				.andExpect(status().isOk());

		mockMvc.perform(get("/projects/{projectId}", projectId).cookie(managerCookie))
				.andExpect(status().isNotFound());
	}

	@Test
	void system_admin은_프로젝트_업무_API를_수행할_수_없다() throws Exception {
		Cookie adminCookie = loginCookie(createUser("system_admin"));
		Cookie managerCookie = loginCookie(createUser("admin"));
		int projectUserId = createUserId("user");
		int projectId = createProject(managerCookie, "admin 담당 프로젝트");

		mockMvc.perform(post("/projects")
						.cookie(adminCookie)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(projectRequest("system_admin 생성 시도"))))
				.andExpect(status().isForbidden());

		mockMvc.perform(get("/projects").cookie(adminCookie))
				.andExpect(status().isForbidden());

		mockMvc.perform(get("/projects/{projectId}", projectId).cookie(adminCookie))
				.andExpect(status().isForbidden());

		mockMvc.perform(post("/projects/{projectId}/assignees/{userId}", projectId, projectUserId)
						.cookie(adminCookie))
				.andExpect(status().isForbidden());
	}

	@Test
	void admin은_프로젝트의_user_담당자를_배정_교체_해제할_수_있다() throws Exception {
		Cookie managerCookie = loginCookie(createUser("admin"));
		int firstUserId = createUserId("user");
		int secondUserId = createUserId("user");
		int replacementUserId = createUserId("user");
		int projectId = createProject(managerCookie, "담당자 연결 프로젝트");

		mockMvc.perform(put("/projects/{projectId}/assignees", projectId)
						.cookie(managerCookie)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of("assigneeUserIds", List.of(firstUserId, secondUserId)))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.assignees", hasSize(2)))
				.andExpect(jsonPath("$.data.assignees[0].userId").value(firstUserId))
				.andExpect(jsonPath("$.data.assignees[0].roleCode").value("user"))
				.andExpect(jsonPath("$.data.assignees[1].userId").value(secondUserId))
				.andExpect(jsonPath("$.data.assignees[1].roleCode").value("user"));

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
				.andExpect(jsonPath("$.data.assignees[0].roleCode").value("user"));

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
	void admin은_scope로_전체와_내_담당_프로젝트를_전환할_수_있다() throws Exception {
		Map<String, String> manager = createUser("admin");
		Cookie managerCookie = loginCookie(manager);
		int managerId = readUserIdFromLogin(manager);
		String prefix = "scope-" + UUID.randomUUID();
		int assignedProjectId = createProject(managerCookie, prefix + "-내담당");
		int otherProjectId = createProject(managerCookie, prefix + "-전체전용");

		mockMvc.perform(post("/projects/{projectId}/assignees/{userId}", assignedProjectId, managerId)
						.cookie(managerCookie))
				.andExpect(status().isOk());

		mockMvc.perform(get("/projects")
						.cookie(managerCookie)
						.param("keyword", prefix))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items", hasSize(2)));

		mockMvc.perform(get("/projects")
						.cookie(managerCookie)
						.param("scope", "all")
						.param("keyword", prefix))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items", hasSize(2)));

		mockMvc.perform(get("/projects")
						.cookie(managerCookie)
						.param("scope", "assigned")
						.param("keyword", prefix))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items", hasSize(1)))
				.andExpect(jsonPath("$.data.items[0].id").value(assignedProjectId));

		mockMvc.perform(get("/projects")
						.cookie(managerCookie)
						.param("scope", "assigned")
						.param("assigneeUserId", String.valueOf(managerId))
						.param("keyword", prefix))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items", hasSize(1)))
				.andExpect(jsonPath("$.data.items[0].id").value(assignedProjectId));

		mockMvc.perform(get("/projects/{projectId}", otherProjectId)
						.cookie(managerCookie))
				.andExpect(status().isOk());
	}

	@Test
	void agent는_admin과_동일하게_프로젝트를_관리할_수_있다() throws Exception {
		Cookie agentCookie = loginCookie(createUser("agent"));
		int projectUserId = createUserId("user");
		int projectId = createProject(agentCookie, "agent 생성 프로젝트");

		mockMvc.perform(post("/projects/{projectId}/assignees/{userId}", projectId, projectUserId)
						.cookie(agentCookie))
				.andExpect(status().isOk());

		mockMvc.perform(get("/projects")
						.cookie(agentCookie)
						.param("assigneeUserId", String.valueOf(projectUserId)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items", hasSize(1)))
				.andExpect(jsonPath("$.data.items[0].id").value(projectId));
	}

	@Test
	void 목록은_담당자명_최신공정률_조치요청을_반환하고_기본정렬한다() throws Exception {
		Map<String, String> manager = createUser("admin");
		Cookie managerCookie = loginCookie(manager);
		int managerId = readUserIdFromLogin(manager);
		int projectUserId = createUserId("user");
		String prefix = "목록계약-" + UUID.randomUUID();
		int lowProgressProjectId = createProject(managerCookie, prefix + "-낮음");
		int highProgressProjectId = createProject(managerCookie, prefix + "-높음");

		mockMvc.perform(post("/projects/{projectId}/assignees/{userId}", lowProgressProjectId, projectUserId)
						.cookie(managerCookie))
				.andExpect(status().isOk());
		mockMvc.perform(post("/projects/{projectId}/assignees/{userId}", highProgressProjectId, projectUserId)
						.cookie(managerCookie))
				.andExpect(status().isOk());

		insertUsageStatement(lowProgressProjectId, "2026-05-01", 10);
		insertUsageStatement(highProgressProjectId, "2026-05-01", 80);
		insertOpenActionRequest(highProgressProjectId, managerId, projectUserId);

		mockMvc.perform(get("/projects")
						.cookie(managerCookie)
						.param("keyword", prefix))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items", hasSize(2)))
				.andExpect(jsonPath("$.data.items[0].id").value(highProgressProjectId))
				.andExpect(jsonPath("$.data.items[0].assigneeNames[0]").value("홍길동"))
				.andExpect(jsonPath("$.data.items[0].assigneeCount").value(1))
				.andExpect(jsonPath("$.data.items[0].latestCumulativeProgressRate").value(80))
				.andExpect(jsonPath("$.data.items[0].hasActionRequest").value(true))
				.andExpect(jsonPath("$.data.items[1].id").value(lowProgressProjectId))
				.andExpect(jsonPath("$.data.items[1].latestCumulativeProgressRate").value(10))
				.andExpect(jsonPath("$.data.items[1].hasActionRequest").value(false));
	}

	@Test
	void 사용내역서는_연월로_최신_revision을_조회할_수_있다() throws Exception {
		Cookie managerCookie = loginCookie(createUser("admin"));
		int projectId = createProject(managerCookie, "월별 사용내역서 프로젝트");

		insertUsageStatement(projectId, "2026-04-01", 1, 30);
		insertUsageStatement(projectId, "2026-04-01", 2, 45);

		mockMvc.perform(get("/projects/{projectId}/usage-statements/by-month", projectId)
						.cookie(managerCookie)
						.param("year", "2026")
						.param("month", "4"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.projectId").value(projectId))
				.andExpect(jsonPath("$.data.statement.reportMonth").value("2026-04-01"))
				.andExpect(jsonPath("$.data.statement.revisionNo").value(2))
				.andExpect(jsonPath("$.data.statement.cumulativeProgressRate").value(45));
	}

	@Test
	void 프로젝트와_아카이브는_미확인_매칭_카운트를_보여주고_mark_api로_확인_처리한다() throws Exception {
		Cookie managerCookie = loginCookie(createUser("admin"));
		Map<String, String> assignee = createUser("user");
		Cookie assigneeCookie = loginCookie(assignee);
		int assigneeId = readUserIdFromLogin(assignee);
		String projectName = "미확인 매칭 프로젝트-" + UUID.randomUUID();
		int projectId = createProject(managerCookie, projectName);
		int statementId = insertUsageStatementId(projectId, "2026-04-01", 1, 30);
		int itemId = insertUsageStatementItem(statementId, "CAT_01");
		int fileId = insertProjectFile(projectId, assigneeId);
		insertEvidenceFileLink(itemId, fileId, "CAT_01", "receipt");

		mockMvc.perform(post("/projects/{projectId}/assignees/{userId}", projectId, assigneeId)
						.cookie(managerCookie))
				.andExpect(status().isOk());

		mockMvc.perform(get("/projects")
						.cookie(managerCookie)
						.param("projectName", projectName))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items[0].id").value(projectId))
				.andExpect(jsonPath("$.data.items[0].uncheckedMatchedFileCount").value(1));
		assertUncheckedLinkCount(projectId, 1);

		mockMvc.perform(get("/projects/{projectId}", projectId)
						.cookie(managerCookie))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.project.uncheckedMatchedFileCount").value(1));
		assertUncheckedLinkCount(projectId, 1);

		mockMvc.perform(get("/projects/{projectId}/archive/categories", projectId)
						.cookie(assigneeCookie))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.uncheckedMatchedFileCount").value(1))
				.andExpect(jsonPath("$.data.items[0].uncheckedMatchedFileCount").value(1));
		assertUncheckedLinkCount(projectId, 1);

		mockMvc.perform(get("/projects/{projectId}/archive/categories", projectId)
						.cookie(managerCookie))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.uncheckedMatchedFileCount").value(1))
				.andExpect(jsonPath("$.data.items[0].uncheckedMatchedFileCount").value(1));
		assertUncheckedLinkCount(projectId, 1);

		mockMvc.perform(post("/projects/{projectId}/archive/mark-checked", projectId)
						.cookie(assigneeCookie))
				.andExpect(status().isForbidden());
		assertUncheckedLinkCount(projectId, 1);

		mockMvc.perform(post("/projects/{projectId}/archive/mark-checked", projectId)
						.cookie(managerCookie))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.projectId").value(projectId))
				.andExpect(jsonPath("$.data.checkedLinkCount").value(1));
		assertUncheckedLinkCount(projectId, 0);

		mockMvc.perform(get("/projects/{projectId}/archive/categories", projectId)
						.cookie(managerCookie))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.uncheckedMatchedFileCount").value(0))
				.andExpect(jsonPath("$.data.items[0].uncheckedMatchedFileCount").value(0));
	}

	@Test
	void 잘못된_sort는_거절한다() throws Exception {
		Cookie managerCookie = loginCookie(createUser("admin"));

		mockMvc.perform(get("/projects")
						.cookie(managerCookie)
						.param("sort", "unknown_sort"))
				.andExpect(status().isBadRequest());

		mockMvc.perform(get("/projects")
						.cookie(managerCookie)
						.param("scope", "unknown_scope"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("scope는 all 또는 assigned만 사용할 수 있습니다."));
	}

	@Test
	void 목록은_페이지네이션과_size_상한을_검증한다() throws Exception {
		Cookie managerCookie = loginCookie(createUser("admin"));
		String prefix = "페이지-" + UUID.randomUUID();
		createProject(managerCookie, projectRequest(prefix + "-1", "PAGE-1", "active", "2026-01-01", "2026-12-31"));
		createProject(managerCookie, projectRequest(prefix + "-2", "PAGE-2", "active", "2026-01-01", "2026-12-31"));
		createProject(managerCookie, projectRequest(prefix + "-3", "PAGE-3", "active", "2026-01-01", "2026-12-31"));

		mockMvc.perform(get("/projects")
						.cookie(managerCookie)
						.param("projectName", prefix)
						.param("sort", "project_name_asc")
						.param("page", "2")
						.param("size", "2"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.page").value(2))
				.andExpect(jsonPath("$.data.size").value(2))
				.andExpect(jsonPath("$.data.totalCount").value(3))
				.andExpect(jsonPath("$.data.totalPages").value(2))
				.andExpect(jsonPath("$.data.items", hasSize(1)))
				.andExpect(jsonPath("$.data.items[0].projectName").value(prefix + "-3"));

		mockMvc.perform(get("/projects")
						.cookie(managerCookie)
						.param("size", "11"))
				.andExpect(status().isBadRequest());

		mockMvc.perform(get("/projects")
						.cookie(managerCookie)
						.param("page", "0"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void 목록은_다중검색조건을_AND로_결합하고_기간겹침과_상태를_필터링한다() throws Exception {
		Cookie managerCookie = loginCookie(createUser("admin"));
		String prefix = "AND-" + UUID.randomUUID();
		int matchedProjectId = createProject(managerCookie, projectRequest(prefix + "-대상", "AND-MATCH-대상", "suspended", "2026-03-01", "2026-05-31"));
		createProject(managerCookie, projectRequest(prefix + "-계약번호불일치", "AND-OTHER", "suspended", "2026-03-01", "2026-05-31"));
		createProject(managerCookie, projectRequest(prefix + "-상태불일치", "AND-MATCH-상태불일치", "active", "2026-03-01", "2026-05-31"));
		createProject(managerCookie, projectRequest(prefix + "-기간불일치", "AND-MATCH-기간불일치", "suspended", "2026-08-01", "2026-09-30"));

		mockMvc.perform(get("/projects")
						.cookie(managerCookie)
						.param("projectName", prefix)
						.param("contractNo", "AND-MATCH")
						.param("status", "suspended")
						.param("periodFrom", "2026-04-15")
						.param("periodTo", "2026-06-15"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items", hasSize(1)))
				.andExpect(jsonPath("$.data.items[0].id").value(matchedProjectId));

		mockMvc.perform(get("/projects")
						.cookie(managerCookie)
						.param("periodFrom", "2026-07-01")
						.param("periodTo", "2026-06-30"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void 목록은_지원하는_정렬옵션을_적용한다() throws Exception {
		Cookie managerCookie = loginCookie(createUser("admin"));
		String prefix = "정렬-" + UUID.randomUUID();
		int alphaProjectId = createProject(managerCookie, projectRequest(prefix + "-A", "SORT-A", "active", "2026-01-01", "2026-04-30"));
		int betaProjectId = createProject(managerCookie, projectRequest(prefix + "-B", "SORT-B", "active", "2026-02-01", "2026-03-31"));
		int gammaProjectId = createProject(managerCookie, projectRequest(prefix + "-C", "SORT-C", "active", "2026-01-15", "2026-02-28"));

		mockMvc.perform(get("/projects")
						.cookie(managerCookie)
						.param("projectName", prefix)
						.param("sort", "project_name_desc"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items[0].id").value(gammaProjectId))
				.andExpect(jsonPath("$.data.items[1].id").value(betaProjectId))
				.andExpect(jsonPath("$.data.items[2].id").value(alphaProjectId));

		mockMvc.perform(get("/projects")
						.cookie(managerCookie)
						.param("projectName", prefix)
						.param("sort", "start_date_asc"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items[0].id").value(alphaProjectId))
				.andExpect(jsonPath("$.data.items[1].id").value(gammaProjectId))
				.andExpect(jsonPath("$.data.items[2].id").value(betaProjectId));

		mockMvc.perform(get("/projects")
						.cookie(managerCookie)
						.param("projectName", prefix)
						.param("sort", "end_date_asc"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items[0].id").value(gammaProjectId))
				.andExpect(jsonPath("$.data.items[1].id").value(betaProjectId))
				.andExpect(jsonPath("$.data.items[2].id").value(alphaProjectId));
	}

	@Test
	void user는_담당자필터를_사용할_수_없다() throws Exception {
		Cookie managerCookie = loginCookie(createUser("admin"));
		Map<String, String> user = createUser("user");
		Cookie userCookie = loginCookie(user);
		int projectUserId = readUserIdFromLogin(user);
		int projectId = createProject(managerCookie, "user 담당자필터 프로젝트");

		mockMvc.perform(post("/projects/{projectId}/assignees/{userId}", projectId, projectUserId)
						.cookie(managerCookie))
				.andExpect(status().isOk());

		mockMvc.perform(get("/projects")
						.cookie(userCookie)
						.param("assigneeUserId", String.valueOf(projectUserId)))
				.andExpect(status().isForbidden());
	}

	@Test
	void user는_본인이_담당한_프로젝트만_조회할_수_있고_수정은_할_수_없다() throws Exception {
		Cookie managerCookie = loginCookie(createUser("admin"));
		Map<String, String> assignee = createUser("user");
		Map<String, String> outsider = createUser("user");
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
						.param("scope", "assigned")
						.param("keyword", "담당 프로젝트"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items", hasSize(1)))
				.andExpect(jsonPath("$.data.items[0].id").value(assignedProjectId));

		mockMvc.perform(get("/projects")
						.cookie(assigneeCookie)
						.param("scope", "all"))
				.andExpect(status().isForbidden());

		mockMvc.perform(get("/projects/{projectId}/assignees", assignedProjectId).cookie(assigneeCookie))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.assignees", hasSize(1)));

		mockMvc.perform(patch("/projects/{projectId}", assignedProjectId)
						.cookie(assigneeCookie)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of("projectName", "user가 수정한 프로젝트"))))
				.andExpect(status().isForbidden());

		mockMvc.perform(post("/projects")
						.cookie(assigneeCookie)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(projectRequest("user 생성 시도"))))
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
						.content(objectMapper.writeValueAsString(Map.of("projectName", "미배정 user 수정 시도"))))
				.andExpect(status().isForbidden());
	}

	private Map<String, Object> projectRequest(String projectName) {
		return projectRequest(projectName, "CN-" + UUID.randomUUID(), "active", "2026-05-01", "2026-12-31");
	}

	private Map<String, Object> projectRequest(
			String projectName,
			String contractNo,
			String status,
			String constructionStartDate,
			String constructionEndDate
	) {
		return Map.of(
				"contractNo", contractNo,
				"constructionCompany", "스칼라건설",
				"projectName", projectName,
				"siteLocation", "서울시 강남구",
				"contractAmount", 100000000,
				"constructionStartDate", constructionStartDate,
				"constructionEndDate", constructionEndDate,
				"appropriatedAmount", 10000000,
				"status", status
		);
	}

	private int createProject(Cookie managerCookie, String projectName) throws Exception {
		return createProject(managerCookie, projectRequest(projectName));
	}

	private int createProject(Cookie managerCookie, Map<String, Object> request) throws Exception {
		MvcResult result = mockMvc.perform(post("/projects")
						.cookie(managerCookie)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isCreated())
				.andReturn();

		return readId(result, "project");
	}

	private int createUserId(String roleCode) throws Exception {
		return readUserIdFromLogin(createUser(roleCode));
	}

	private void insertUsageStatement(int projectId, String reportMonth, int progressRate) {
		insertUsageStatement(projectId, reportMonth, 1, progressRate);
	}

	private void insertUsageStatement(int projectId, String reportMonth, int revisionNo, int progressRate) {
		jdbcTemplate.update("""
				INSERT INTO service.usage_statements
					(project_id, report_month, revision_no, document_written_date, cumulative_progress_rate)
				VALUES (?, ?::date, ?, ?::date, ?)
				""", projectId, reportMonth, revisionNo, reportMonth, progressRate);
	}

	private int insertUsageStatementId(int projectId, String reportMonth, int revisionNo, int progressRate) {
		return jdbcTemplate.queryForObject("""
				INSERT INTO service.usage_statements
					(project_id, report_month, revision_no, document_written_date, cumulative_progress_rate)
				VALUES (?, ?::date, ?, ?::date, ?)
				RETURNING id
				""", Integer.class, projectId, reportMonth, revisionNo, reportMonth, progressRate);
	}

	private int insertUsageStatementItem(int usageStatementId, String categoryCode) {
		return jdbcTemplate.queryForObject("""
				INSERT INTO service.usage_statement_items
					(usage_statement_id, category_code, used_on, item_name, quantity, unit_price, total_amount, page_no)
				VALUES (?, ?, '2026-04-01', '테스트 항목', 1, 1000, 1000, 1)
				RETURNING id
				""", Integer.class, usageStatementId, categoryCode);
	}

	private int insertProjectFile(int projectId, int uploadedByUserId) {
		return jdbcTemplate.queryForObject("""
				INSERT INTO service.files
					(project_id, uploaded_by_user_id, uploaded_evidence_type_code, original_filename, storage_key, mime_type, size_bytes)
				VALUES (?, ?, 'receipt', 'receipt.pdf', ?, 'application/pdf', 1024)
				RETURNING id
				""", Integer.class, projectId, uploadedByUserId, "tests/" + UUID.randomUUID() + "/receipt.pdf");
	}

	private void insertEvidenceFileLink(int itemId, int fileId, String categoryCode, String evidenceTypeCode) {
		jdbcTemplate.update("""
				INSERT INTO service.evidence_file_links
					(usage_statement_item_id, file_id, category_code, evidence_type_code)
				VALUES (?, ?, ?, ?)
				""", itemId, fileId, categoryCode, evidenceTypeCode);
	}

	private void assertUncheckedLinkCount(int projectId, int expectedCount) {
		Integer count = jdbcTemplate.queryForObject("""
				SELECT COUNT(*)::int
				FROM service.evidence_file_links l
				JOIN service.usage_statement_items i ON i.id = l.usage_statement_item_id
				JOIN service.usage_statements s ON s.id = i.usage_statement_id
				WHERE s.project_id = ?
					AND l.checked_at IS NULL
				""", Integer.class, projectId);
		assertThat(count).isEqualTo(expectedCount);
	}

	private void insertOpenActionRequest(int projectId, int requestedByUserId, int assigneeUserId) {
		jdbcTemplate.update("""
				INSERT INTO service.action_requests
					(project_id, requested_by_user_id, assignee_user_id, title, status_code)
				VALUES (?, ?, ?, '테스트 조치 요청', 'open')
				""", projectId, requestedByUserId, assigneeUserId);
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
