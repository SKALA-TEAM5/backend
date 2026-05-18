package com.skala.backend.action.controller;

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
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ActionRequestControllerTest {

	@Autowired MockMvc mockMvc;
	@Autowired ObjectMapper objectMapper;
	@Autowired JdbcTemplate jdbcTemplate;
	@Autowired UserRepository userRepository;
	@Autowired PasswordEncoder passwordEncoder;

	// ─── R-38: 생성 ──────────────────────────────────────────────────────

	@Test
	void admin은_조치_요청을_생성할_수_있다() throws Exception {
		Cookie adminCookie = loginCookie(createUser("admin"));
		Map<String, String> assignee = createUser("user");
		int assigneeId = readUserId(assignee);
		int projectId = createProject(adminCookie);

		mockMvc.perform(post("/projects/{pid}/action-requests", projectId)
						.cookie(adminCookie)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of(
								"title", "안전모 미착용 조치 요청",
								"assigneeUserId", assigneeId
						))))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.id").isNumber())
				.andExpect(jsonPath("$.data.title").value("안전모 미착용 조치 요청"))
				.andExpect(jsonPath("$.data.statusCode").value("open"))
				.andExpect(jsonPath("$.data.assigneeUserId").value(assigneeId))
				.andExpect(jsonPath("$.data.projectId").value(projectId));
	}

	@Test
	void admin은_선택_필드를_포함하여_조치_요청을_생성할_수_있다() throws Exception {
		Cookie adminCookie = loginCookie(createUser("admin"));
		Map<String, String> assignee = createUser("user");
		int assigneeId = readUserId(assignee);
		int projectId = createProject(adminCookie);
		int statementId = insertStatement(projectId);

		mockMvc.perform(post("/projects/{pid}/action-requests", projectId)
						.cookie(adminCookie)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of(
								"title", "현장 안전망 설치 요청",
								"reason", "사진 검토 결과 안전망 미설치 확인",
								"assigneeUserId", assigneeId,
								"usageStatementId", statementId,
								"dueDate", "2026-06-30"
						))))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.reason").value("사진 검토 결과 안전망 미설치 확인"))
				.andExpect(jsonPath("$.data.usageStatementId").value(statementId))
				.andExpect(jsonPath("$.data.dueDate").value("2026-06-30"));
	}

	@Test
	void user는_조치_요청을_생성할_수_없다() throws Exception {
		Cookie adminCookie = loginCookie(createUser("admin"));
		Map<String, String> user = createUser("user");
		Cookie userCookie = loginCookie(user);
		int userId = readUserId(user);
		int projectId = createProject(adminCookie);
		assign(adminCookie, projectId, userId);

		mockMvc.perform(post("/projects/{pid}/action-requests", projectId)
						.cookie(userCookie)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of(
								"title", "user 생성 시도",
								"assigneeUserId", userId
						))))
				.andExpect(status().isForbidden());
	}

	@Test
	void title이_없으면_조치_요청_생성이_거부된다() throws Exception {
		Cookie adminCookie = loginCookie(createUser("admin"));
		Map<String, String> assignee = createUser("user");
		int assigneeId = readUserId(assignee);
		int projectId = createProject(adminCookie);

		mockMvc.perform(post("/projects/{pid}/action-requests", projectId)
						.cookie(adminCookie)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of(
								"assigneeUserId", assigneeId
						))))
				.andExpect(status().isBadRequest());
	}

	@Test
	void assigneeUserId가_없으면_조치_요청_생성이_거부된다() throws Exception {
		Cookie adminCookie = loginCookie(createUser("admin"));
		int projectId = createProject(adminCookie);

		mockMvc.perform(post("/projects/{pid}/action-requests", projectId)
						.cookie(adminCookie)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of(
								"title", "담당자 없는 요청"
						))))
				.andExpect(status().isBadRequest());
	}

	// ─── R-39: 상태 전환 ──────────────────────────────────────────────────

	@Test
	void open에서_in_progress로_전환할_수_있다() throws Exception {
		Cookie adminCookie = loginCookie(createUser("admin"));
		Map<String, String> assignee = createUser("user");
		int assigneeId = readUserId(assignee);
		int projectId = createProject(adminCookie);
		int actionRequestId = insertActionRequest(projectId, assigneeId, "open");

		mockMvc.perform(patch("/projects/{pid}/action-requests/{rid}/status", projectId, actionRequestId)
						.cookie(adminCookie)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of("statusCode", "in_progress"))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.statusCode").value("in_progress"));
	}

	@Test
	void in_progress에서_resolved로_전환하면_resolvedAt이_자동_설정된다() throws Exception {
		Cookie adminCookie = loginCookie(createUser("admin"));
		Map<String, String> assignee = createUser("user");
		int assigneeId = readUserId(assignee);
		int projectId = createProject(adminCookie);
		int actionRequestId = insertActionRequest(projectId, assigneeId, "in_progress");

		mockMvc.perform(patch("/projects/{pid}/action-requests/{rid}/status", projectId, actionRequestId)
						.cookie(adminCookie)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of("statusCode", "resolved"))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.statusCode").value("resolved"))
				.andExpect(jsonPath("$.data.resolvedAt", notNullValue()));
	}

	@Test
	void resolved에서_closed로_전환할_수_있다() throws Exception {
		Cookie adminCookie = loginCookie(createUser("admin"));
		Map<String, String> assignee = createUser("user");
		int assigneeId = readUserId(assignee);
		int projectId = createProject(adminCookie);
		int actionRequestId = insertActionRequest(projectId, assigneeId, "resolved");

		mockMvc.perform(patch("/projects/{pid}/action-requests/{rid}/status", projectId, actionRequestId)
						.cookie(adminCookie)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of("statusCode", "closed"))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.statusCode").value("closed"));
	}

	@Test
	void 담당자_user도_상태를_전환할_수_있다() throws Exception {
		Cookie adminCookie = loginCookie(createUser("admin"));
		Map<String, String> user = createUser("user");
		Cookie userCookie = loginCookie(user);
		int userId = readUserId(user);
		int projectId = createProject(adminCookie);
		assign(adminCookie, projectId, userId);
		int actionRequestId = insertActionRequest(projectId, userId, "open");

		mockMvc.perform(patch("/projects/{pid}/action-requests/{rid}/status", projectId, actionRequestId)
						.cookie(userCookie)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of("statusCode", "in_progress"))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.statusCode").value("in_progress"));
	}

	@Test
	void open에서_resolved로_직접_전환하면_409를_반환한다() throws Exception {
		Cookie adminCookie = loginCookie(createUser("admin"));
		Map<String, String> assignee = createUser("user");
		int assigneeId = readUserId(assignee);
		int projectId = createProject(adminCookie);
		int actionRequestId = insertActionRequest(projectId, assigneeId, "open");

		mockMvc.perform(patch("/projects/{pid}/action-requests/{rid}/status", projectId, actionRequestId)
						.cookie(adminCookie)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of("statusCode", "resolved"))))
				.andExpect(status().isConflict());
	}

	@Test
	void open에서_closed로_직접_전환하면_409를_반환한다() throws Exception {
		Cookie adminCookie = loginCookie(createUser("admin"));
		Map<String, String> assignee = createUser("user");
		int assigneeId = readUserId(assignee);
		int projectId = createProject(adminCookie);
		int actionRequestId = insertActionRequest(projectId, assigneeId, "open");

		mockMvc.perform(patch("/projects/{pid}/action-requests/{rid}/status", projectId, actionRequestId)
						.cookie(adminCookie)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of("statusCode", "closed"))))
				.andExpect(status().isConflict());
	}

	@Test
	void open은_유효하지_않은_대상_상태이므로_400을_반환한다() throws Exception {
		Cookie adminCookie = loginCookie(createUser("admin"));
		Map<String, String> assignee = createUser("user");
		int assigneeId = readUserId(assignee);
		int projectId = createProject(adminCookie);
		int actionRequestId = insertActionRequest(projectId, assigneeId, "in_progress");

		// "open"은 초기 상태로만 사용되며 전환 대상으로 지정할 수 없다 → 400 BAD_REQUEST
		mockMvc.perform(patch("/projects/{pid}/action-requests/{rid}/status", projectId, actionRequestId)
						.cookie(adminCookie)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of("statusCode", "open"))))
				.andExpect(status().isBadRequest());
	}

	@Test
	void in_progress_상태에서_in_progress로_다시_전환하면_409를_반환한다() throws Exception {
		Cookie adminCookie = loginCookie(createUser("admin"));
		Map<String, String> assignee = createUser("user");
		int assigneeId = readUserId(assignee);
		int projectId = createProject(adminCookie);
		int actionRequestId = insertActionRequest(projectId, assigneeId, "in_progress");

		mockMvc.perform(patch("/projects/{pid}/action-requests/{rid}/status", projectId, actionRequestId)
						.cookie(adminCookie)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of("statusCode", "in_progress"))))
				.andExpect(status().isConflict());
	}

	@Test
	void resolved에서_in_progress로_역행하면_409를_반환한다() throws Exception {
		Cookie adminCookie = loginCookie(createUser("admin"));
		Map<String, String> assignee = createUser("user");
		int assigneeId = readUserId(assignee);
		int projectId = createProject(adminCookie);
		int actionRequestId = insertActionRequest(projectId, assigneeId, "resolved");

		mockMvc.perform(patch("/projects/{pid}/action-requests/{rid}/status", projectId, actionRequestId)
						.cookie(adminCookie)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of("statusCode", "in_progress"))))
				.andExpect(status().isConflict());
	}

	@Test
	void closed_상태에서_어떤_전환도_409를_반환한다() throws Exception {
		Cookie adminCookie = loginCookie(createUser("admin"));
		Map<String, String> assignee = createUser("user");
		int assigneeId = readUserId(assignee);
		int projectId = createProject(adminCookie);
		int actionRequestId = insertActionRequest(projectId, assigneeId, "closed");

		mockMvc.perform(patch("/projects/{pid}/action-requests/{rid}/status", projectId, actionRequestId)
						.cookie(adminCookie)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of("statusCode", "resolved"))))
				.andExpect(status().isConflict());
	}

	@Test
	void 미배정_user는_상태_전환을_할_수_없다() throws Exception {
		Cookie adminCookie = loginCookie(createUser("admin"));
		Cookie outsiderCookie = loginCookie(createUser("user"));
		Map<String, String> assignee = createUser("user");
		int assigneeId = readUserId(assignee);
		int projectId = createProject(adminCookie);
		int actionRequestId = insertActionRequest(projectId, assigneeId, "open");

		mockMvc.perform(patch("/projects/{pid}/action-requests/{rid}/status", projectId, actionRequestId)
						.cookie(outsiderCookie)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of("statusCode", "in_progress"))))
				.andExpect(status().isForbidden());
	}

	@Test
	void 존재하지_않는_조치_요청_상태_전환은_404를_반환한다() throws Exception {
		Cookie adminCookie = loginCookie(createUser("admin"));
		int projectId = createProject(adminCookie);

		mockMvc.perform(patch("/projects/{pid}/action-requests/{rid}/status", projectId, 999_999_999L)
						.cookie(adminCookie)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of("statusCode", "in_progress"))))
				.andExpect(status().isNotFound());
	}

	@Test
	void 다른_프로젝트_조치_요청_상태_전환은_404를_반환한다() throws Exception {
		Cookie adminCookie = loginCookie(createUser("admin"));
		Map<String, String> assignee = createUser("user");
		int assigneeId = readUserId(assignee);
		int projectId = createProject(adminCookie);
		int otherProjectId = createProject(adminCookie);
		int otherActionRequestId = insertActionRequest(otherProjectId, assigneeId, "open");

		mockMvc.perform(patch("/projects/{pid}/action-requests/{rid}/status", projectId, otherActionRequestId)
						.cookie(adminCookie)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of("statusCode", "in_progress"))))
				.andExpect(status().isNotFound());
	}

	// ─── R-40: 목록·상세 조회 ─────────────────────────────────────────────

	@Test
	void admin은_조치_요청_목록을_생성일_내림차순으로_조회한다() throws Exception {
		Cookie adminCookie = loginCookie(createUser("admin"));
		Map<String, String> assignee = createUser("user");
		int assigneeId = readUserId(assignee);
		int projectId = createProject(adminCookie);

		int firstId = insertActionRequest(projectId, assigneeId, "open");
		int secondId = insertActionRequest(projectId, assigneeId, "in_progress");

		mockMvc.perform(get("/projects/{pid}/action-requests", projectId)
						.cookie(adminCookie))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data", hasSize(2)))
				.andExpect(jsonPath("$.data[0].id").value(secondId))
				.andExpect(jsonPath("$.data[1].id").value(firstId));
	}

	@Test
	void 담당자_user는_조치_요청_목록을_조회할_수_있다() throws Exception {
		Cookie adminCookie = loginCookie(createUser("admin"));
		Map<String, String> user = createUser("user");
		Cookie userCookie = loginCookie(user);
		int userId = readUserId(user);
		int projectId = createProject(adminCookie);
		assign(adminCookie, projectId, userId);
		insertActionRequest(projectId, userId, "open");

		mockMvc.perform(get("/projects/{pid}/action-requests", projectId)
						.cookie(userCookie))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data", hasSize(1)));
	}

	@Test
	void 미배정_user는_조치_요청_목록을_조회할_수_없다() throws Exception {
		Cookie adminCookie = loginCookie(createUser("admin"));
		Cookie outsiderCookie = loginCookie(createUser("user"));
		int projectId = createProject(adminCookie);

		mockMvc.perform(get("/projects/{pid}/action-requests", projectId)
						.cookie(outsiderCookie))
				.andExpect(status().isForbidden());
	}

	@Test
	void admin은_조치_요청_상세를_조회할_수_있다() throws Exception {
		Cookie adminCookie = loginCookie(createUser("admin"));
		Map<String, String> assignee = createUser("user");
		int assigneeId = readUserId(assignee);
		int projectId = createProject(adminCookie);
		int statementId = insertStatement(projectId);
		int actionRequestId = insertActionRequestFull(projectId, assigneeId, statementId, "안전망 보완 요청", "open");

		mockMvc.perform(get("/projects/{pid}/action-requests/{rid}", projectId, actionRequestId)
						.cookie(adminCookie))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.id").value(actionRequestId))
				.andExpect(jsonPath("$.data.title").value("안전망 보완 요청"))
				.andExpect(jsonPath("$.data.assigneeUserId").value(assigneeId))
				.andExpect(jsonPath("$.data.usageStatementId").value(statementId))
				.andExpect(jsonPath("$.data.statusCode").value("open"))
				.andExpect(jsonPath("$.data.createdAt", notNullValue()));
	}

	@Test
	void 다른_프로젝트_조치_요청_상세는_조회할_수_없다() throws Exception {
		Cookie adminCookie = loginCookie(createUser("admin"));
		Map<String, String> assignee = createUser("user");
		int assigneeId = readUserId(assignee);
		int projectId = createProject(adminCookie);
		int otherProjectId = createProject(adminCookie);
		int otherActionRequestId = insertActionRequest(otherProjectId, assigneeId, "open");

		mockMvc.perform(get("/projects/{pid}/action-requests/{rid}", projectId, otherActionRequestId)
						.cookie(adminCookie))
				.andExpect(status().isNotFound());
	}

	@Test
	void 존재하지_않는_조치_요청_상세는_404를_반환한다() throws Exception {
		Cookie adminCookie = loginCookie(createUser("admin"));
		int projectId = createProject(adminCookie);

		mockMvc.perform(get("/projects/{pid}/action-requests/{rid}", projectId, 999_999_999L)
						.cookie(adminCookie))
				.andExpect(status().isNotFound());
	}

	// ─── 인증 없는 요청 ──────────────────────────────────────────────────

	@Test
	void 쿠키_없이_조치_요청_목록을_조회하면_401을_반환한다() throws Exception {
		Cookie adminCookie = loginCookie(createUser("admin"));
		int projectId = createProject(adminCookie);

		mockMvc.perform(get("/projects/{pid}/action-requests", projectId))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void 유효하지_않은_상태코드로_전환하면_400을_반환한다() throws Exception {
		Cookie adminCookie = loginCookie(createUser("admin"));
		Map<String, String> assignee = createUser("user");
		int assigneeId = readUserId(assignee);
		int projectId = createProject(adminCookie);
		int actionRequestId = insertActionRequest(projectId, assigneeId, "open");

		mockMvc.perform(patch("/projects/{pid}/action-requests/{rid}/status", projectId, actionRequestId)
						.cookie(adminCookie)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of("statusCode", "flying"))))
				.andExpect(status().isBadRequest());
	}

	// ─── 전체 플로우 통합 ────────────────────────────────────────────────

	@Test
	void 조치_요청_생성_후_open_in_progress_resolved_closed_전체_플로우가_정상_동작한다() throws Exception {
		Cookie adminCookie = loginCookie(createUser("admin"));
		Map<String, String> assignee = createUser("user");
		Cookie assigneeCookie = loginCookie(assignee);
		int assigneeId = readUserId(assignee);
		int projectId = createProject(adminCookie);
		assign(adminCookie, projectId, assigneeId);

		// 생성 (API 검증)
		MvcResult createResult = mockMvc.perform(post("/projects/{pid}/action-requests", projectId)
						.cookie(adminCookie)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of(
								"title", "전체 플로우 테스트",
								"assigneeUserId", assigneeId
						))))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.statusCode").value("open"))
				.andReturn();

		int actionRequestId = objectMapper.readTree(createResult.getResponse().getContentAsString())
				.path("data").path("id").asInt();

		// 담당자가 진행 시작
		mockMvc.perform(patch("/projects/{pid}/action-requests/{rid}/status", projectId, actionRequestId)
						.cookie(assigneeCookie)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of("statusCode", "in_progress"))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.statusCode").value("in_progress"));

		// 담당자가 완료 처리
		mockMvc.perform(patch("/projects/{pid}/action-requests/{rid}/status", projectId, actionRequestId)
						.cookie(assigneeCookie)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of("statusCode", "resolved"))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.statusCode").value("resolved"))
				.andExpect(jsonPath("$.data.resolvedAt", notNullValue()));

		// admin이 종료
		mockMvc.perform(patch("/projects/{pid}/action-requests/{rid}/status", projectId, actionRequestId)
						.cookie(adminCookie)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of("statusCode", "closed"))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.statusCode").value("closed"));

		// resolved_at DB 제약조건 충족 검증
		Integer count = jdbcTemplate.queryForObject(
				"SELECT count(*)::int FROM service.action_requests WHERE id = ? AND resolved_at IS NOT NULL",
				Integer.class, actionRequestId);
		assertThat(count).isEqualTo(1);
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
		return jdbcTemplate.queryForObject("""
				INSERT INTO service.usage_statements
					(project_id, report_month, revision_no, document_written_date, cumulative_progress_rate)
				VALUES (?, '2026-05-01'::date, 1, '2026-05-15'::date, 30)
				RETURNING id
				""", Integer.class, projectId);
	}

	// JPA 1차 캐시를 우회해 특정 상태의 조치 요청을 fixture로 직접 생성한다.
	// clock_timestamp()를 사용해 @Transactional 환경에서도 단조 증가하는 created_at을 보장한다.
	// (now()는 트랜잭션 시작 시각으로 고정되어 순서 테스트 시 타이 발생)
	// resolved/closed는 DB 제약조건(resolved_at NOT NULL) 충족을 위해 resolved_at을 함께 설정한다.
	private int insertActionRequest(int projectId, int assigneeId, String statusCode) {
		if ("resolved".equals(statusCode) || "closed".equals(statusCode)) {
			return jdbcTemplate.queryForObject("""
					INSERT INTO service.action_requests
						(project_id, requested_by_user_id, assignee_user_id, title, status_code, created_at, resolved_at)
					VALUES (?, ?, ?, ?, ?, clock_timestamp(), clock_timestamp())
					RETURNING id
					""", Integer.class, projectId, assigneeId, assigneeId,
					"테스트 조치 요청-" + UUID.randomUUID(), statusCode);
		}
		return jdbcTemplate.queryForObject("""
				INSERT INTO service.action_requests
					(project_id, requested_by_user_id, assignee_user_id, title, status_code, created_at)
				VALUES (?, ?, ?, ?, ?, clock_timestamp())
				RETURNING id
				""", Integer.class, projectId, assigneeId, assigneeId,
				"테스트 조치 요청-" + UUID.randomUUID(), statusCode);
	}

	private int insertActionRequestFull(int projectId, int assigneeId, int usageStatementId, String title, String statusCode) {
		return jdbcTemplate.queryForObject("""
				INSERT INTO service.action_requests
					(project_id, requested_by_user_id, assignee_user_id, usage_statement_id, title, status_code)
				VALUES (?, ?, ?, ?, ?, ?)
				RETURNING id
				""", Integer.class, projectId, assigneeId, assigneeId, usageStatementId, title, statusCode);
	}
}
