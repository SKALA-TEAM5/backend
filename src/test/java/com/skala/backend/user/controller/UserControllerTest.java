package com.skala.backend.user.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class UserControllerTest {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	ObjectMapper objectMapper;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Test
	void 시스템_admin은_사용자_계정을_CRUD_할_수_있다() throws Exception {
		Cookie adminCookie = loginCookie(createUser("admin"));
		String employeeNo = "EMP-" + UUID.randomUUID();

		MvcResult createResult = mockMvc.perform(post("/users")
						.cookie(adminCookie)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of(
								"employeeNo", employeeNo,
								"realName", "김담당",
								"password", "P@ssw0rd123!",
								"roleCode", "site"
						))))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.user.employeeNo").value(employeeNo))
				.andExpect(jsonPath("$.data.user.realName").value("김담당"))
				.andExpect(jsonPath("$.data.user.roleCode").value("site"))
				.andExpect(jsonPath("$.data.user.password").doesNotExist())
				.andExpect(jsonPath("$.data.user.passwordHash").doesNotExist())
				.andReturn();

		Integer userId = readUserId(createResult);

		mockMvc.perform(get("/users")
						.cookie(adminCookie)
						.param("roleCode", "site")
						.param("keyword", employeeNo))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items", hasSize(1)))
				.andExpect(jsonPath("$.data.items[0].id").value(userId));

		mockMvc.perform(get("/users/{userId}", userId)
						.cookie(adminCookie))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.user.employeeNo").value(employeeNo));

		mockMvc.perform(patch("/users/{userId}", userId)
						.cookie(adminCookie)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of(
								"realName", "김수정",
								"password", "N3wP@ssw0rd!",
								"roleCode", "agent"
						))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.user.realName").value("김수정"))
				.andExpect(jsonPath("$.data.user.roleCode").value("agent"));

		mockMvc.perform(delete("/users/{userId}", userId)
						.cookie(adminCookie))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true));

		mockMvc.perform(get("/users/{userId}", userId)
						.cookie(adminCookie))
				.andExpect(status().isNotFound());
	}

	@Test
	void 시스템_admin은_사용자를_생성할_때_권한을_부여할_수_있다() throws Exception {
		Cookie adminCookie = loginCookie(createUser("admin"));

		for (String roleCode : new String[] {"admin", "hq", "site", "agent"}) {
			mockMvc.perform(post("/users")
							.cookie(adminCookie)
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(Map.of(
									"employeeNo", "EMP-" + UUID.randomUUID(),
									"realName", "권한부여대상",
									"password", "P@ssw0rd123!",
									"roleCode", roleCode
							))))
					.andExpect(status().isCreated())
					.andExpect(jsonPath("$.data.user.roleCode").value(roleCode));
		}
	}

	@Test
	void 시스템_admin은_사용자_목록을_권한과_키워드로_필터링할_수_있다() throws Exception {
		Cookie adminCookie = loginCookie(createUser("admin"));
		String targetEmployeeNo = "EMP-" + UUID.randomUUID();

		createUserByAdmin(adminCookie, targetEmployeeNo, "검색대상", "site");
		createUserByAdmin(adminCookie, "EMP-" + UUID.randomUUID(), "검색대상", "agent");
		createUserByAdmin(adminCookie, "EMP-" + UUID.randomUUID(), "다른이름", "site");

		mockMvc.perform(get("/users")
						.cookie(adminCookie)
						.param("roleCode", "site")
						.param("keyword", "검색대상"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items", hasSize(1)))
				.andExpect(jsonPath("$.data.items[0].employeeNo").value(targetEmployeeNo))
				.andExpect(jsonPath("$.data.items[0].roleCode").value("site"));

		mockMvc.perform(get("/users")
						.cookie(adminCookie)
						.param("keyword", targetEmployeeNo.substring(0, 8)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items", hasSize(1)))
				.andExpect(jsonPath("$.data.items[0].employeeNo").value(targetEmployeeNo));
	}

	@Test
	void 시스템_admin은_사용자_비밀번호를_변경할_수_있고_기존_비밀번호는_더_이상_동작하지_않는다() throws Exception {
		Cookie adminCookie = loginCookie(createUser("admin"));
		String employeeNo = "EMP-" + UUID.randomUUID();
		String oldPassword = "P@ssw0rd123!";
		String newPassword = "N3wP@ssw0rd!";
		Integer userId = createUserByAdmin(adminCookie, employeeNo, "비밀번호변경대상", "site");

		mockMvc.perform(patch("/users/{userId}", userId)
						.cookie(adminCookie)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of("password", newPassword))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.user.passwordHash").doesNotExist());

		mockMvc.perform(post("/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of(
								"employeeNo", employeeNo,
								"password", oldPassword
						))))
				.andExpect(status().isUnauthorized());

		mockMvc.perform(post("/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of(
								"employeeNo", employeeNo,
								"password", newPassword
						))))
				.andExpect(status().isOk())
				.andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("access_token=")));
	}

	@Test
	void 시스템_admin의_사용자_생성은_사번_중복과_유효하지_않은_요청을_거부한다() throws Exception {
		Cookie adminCookie = loginCookie(createUser("admin"));
		String employeeNo = "EMP-" + UUID.randomUUID();

		createUserByAdmin(adminCookie, employeeNo, "중복대상", "site");

		mockMvc.perform(post("/users")
						.cookie(adminCookie)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of(
								"employeeNo", employeeNo,
								"realName", "중복대상2",
								"password", "P@ssw0rd123!",
								"roleCode", "site"
						))))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value("이미 존재하는 사번입니다."));

		mockMvc.perform(post("/users")
						.cookie(adminCookie)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of(
								"employeeNo", "EMP-" + UUID.randomUUID(),
								"realName", "짧은비밀번호",
								"password", "short",
								"roleCode", "site"
						))))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value(containsString("password:")));
	}

	@Test
	void 시스템_admin의_사용자_생성은_필수값_누락과_공백값을_거부한다() throws Exception {
		Cookie adminCookie = loginCookie(createUser("admin"));

		mockMvc.perform(post("/users")
						.cookie(adminCookie)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of(
								"realName", "사번없음",
								"password", "P@ssw0rd123!",
								"roleCode", "site"
						))))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value(containsString("employeeNo:")));

		mockMvc.perform(post("/users")
						.cookie(adminCookie)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of(
								"employeeNo", "   ",
								"realName", "공백사번",
								"password", "P@ssw0rd123!",
								"roleCode", "site"
						))))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value(containsString("employeeNo:")));

		mockMvc.perform(post("/users")
						.cookie(adminCookie)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of(
								"employeeNo", "EMP-" + UUID.randomUUID(),
								"realName", "권한없음",
								"password", "P@ssw0rd123!"
						))))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value(containsString("roleCode:")));
	}

	@Test
	void 시스템_admin의_사용자_생성은_길이_제약을_검증한다() throws Exception {
		Cookie adminCookie = loginCookie(createUser("admin"));

		mockMvc.perform(post("/users")
						.cookie(adminCookie)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of(
								"employeeNo", "E".repeat(51),
								"realName", "길이검증",
								"password", "P@ssw0rd123!",
								"roleCode", "site"
						))))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value(containsString("employeeNo:")));

		mockMvc.perform(post("/users")
						.cookie(adminCookie)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of(
								"employeeNo", "EMP-" + UUID.randomUUID(),
								"realName", "가".repeat(101),
								"password", "P@ssw0rd123!",
								"roleCode", "site"
						))))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value(containsString("realName:")));
	}

	@Test
	void users_API는_이전_user_권한값을_거부한다() throws Exception {
		Cookie adminCookie = loginCookie(createUser("admin"));

		mockMvc.perform(post("/users")
						.cookie(adminCookie)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of(
								"employeeNo", "EMP-" + UUID.randomUUID(),
								"realName", "이전권한",
								"password", "P@ssw0rd123!",
								"roleCode", "user"
						))))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("요청 본문을 읽을 수 없습니다."));

		mockMvc.perform(get("/users")
						.cookie(adminCookie)
						.param("roleCode", "user"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("잘못된 요청 파라미터입니다."));
	}

	@Test
	void 시스템_admin의_사용자_수정은_빈_본문과_존재하지_않는_사용자를_거부한다() throws Exception {
		Cookie adminCookie = loginCookie(createUser("admin"));

		mockMvc.perform(patch("/users/{userId}", 999_999_999L)
						.cookie(adminCookie)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("수정할 값이 없습니다."));

		mockMvc.perform(patch("/users/{userId}", 999_999_999L)
						.cookie(adminCookie)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of("realName", "없는사용자"))))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.message").value("사용자를 찾을 수 없습니다."));
	}

	@Test
	void 시스템_admin의_사용자_수정은_공백이름_짧은비밀번호_유효하지_않은권한을_거부한다() throws Exception {
		Cookie adminCookie = loginCookie(createUser("admin"));
		Integer userId = createUserByAdmin(adminCookie, "EMP-" + UUID.randomUUID(), "수정검증대상", "site");

		mockMvc.perform(patch("/users/{userId}", userId)
						.cookie(adminCookie)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of("realName", "   "))))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("realName: 공백일 수 없습니다."));

		mockMvc.perform(patch("/users/{userId}", userId)
						.cookie(adminCookie)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of("password", "short"))))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value(containsString("password:")));

		mockMvc.perform(patch("/users/{userId}", userId)
						.cookie(adminCookie)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of("roleCode", "user"))))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("요청 본문을 읽을 수 없습니다."));
	}

	@Test
	void 시스템_admin은_사용자_목록검색_키워드의_앞뒤_공백을_무시한다() throws Exception {
		Cookie adminCookie = loginCookie(createUser("admin"));
		String employeeNo = "EMP-" + UUID.randomUUID();
		createUserByAdmin(adminCookie, employeeNo, "공백검색대상", "site");

		mockMvc.perform(get("/users")
						.cookie(adminCookie)
						.param("keyword", "  " + employeeNo + "  "))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items", hasSize(1)))
				.andExpect(jsonPath("$.data.items[0].employeeNo").value(employeeNo));
	}

	@Test
	void 시스템_admin의_사용자_삭제는_존재하지_않는_사용자를_거부한다() throws Exception {
		Cookie adminCookie = loginCookie(createUser("admin"));

		mockMvc.perform(delete("/users/{userId}", 999_999_999L)
						.cookie(adminCookie))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.message").value("사용자를 찾을 수 없습니다."));
	}

	@Test
	void 시스템_admin이_아니면_사용자_관리_API에_접근할_수_없다() throws Exception {
		for (String roleCode : new String[] {"hq", "site", "agent"}) {
			Cookie cookie = loginCookie(createUser(roleCode));

			mockMvc.perform(get("/users").cookie(cookie))
					.andExpect(status().isForbidden())
					.andExpect(jsonPath("$.message").value("권한이 없습니다."));

			mockMvc.perform(post("/users")
							.cookie(cookie)
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(Map.of(
									"employeeNo", "EMP-" + UUID.randomUUID(),
									"realName", "권한없음",
									"password", "P@ssw0rd123!",
									"roleCode", "site"
							))))
					.andExpect(status().isForbidden());
		}
	}

	@Test
	void 인증되지_않거나_유효하지_않은_쿠키면_users_API를_사용할_수_없다() throws Exception {
		mockMvc.perform(get("/users"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.message").value("인증이 필요합니다."));

		mockMvc.perform(get("/users/me"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.message").value("인증이 필요합니다."));

		mockMvc.perform(get("/users/me").cookie(new Cookie("access_token", "not-a-number")))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.message").value("유효하지 않은 인증 정보입니다."));

		mockMvc.perform(get("/users/me").cookie(new Cookie("access_token", "999999999")))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.message").value("유효하지 않은 인증 정보입니다."));
	}

	@Test
	void 로그인_사용자는_내_프로필을_조회하고_탈퇴할_수_있다() throws Exception {
		Map<String, String> signupRequest = createUser("site");
		Cookie userCookie = loginCookie(signupRequest);

		mockMvc.perform(get("/users/me").cookie(userCookie))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.user.employeeNo").value(signupRequest.get("employeeNo")))
				.andExpect(jsonPath("$.data.user.realName").value(signupRequest.get("realName")))
				.andExpect(jsonPath("$.data.user.password").doesNotExist())
				.andExpect(jsonPath("$.data.user.passwordHash").doesNotExist());

		mockMvc.perform(delete("/users/me")
						.cookie(userCookie)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of("password", "wrong-password"))))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.message").value("비밀번호가 올바르지 않습니다."));

		mockMvc.perform(delete("/users/me")
						.cookie(userCookie)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of("password", signupRequest.get("password")))))
				.andExpect(status().isOk())
				.andExpect(header().string(HttpHeaders.SET_COOKIE, allOf(
						containsString("access_token="),
						containsString("Max-Age=0"),
						containsString("Path=/")
				)))
				.andExpect(jsonPath("$.success").value(true));
	}

	@Test
	void 내_프로필_탈퇴는_비밀번호_필수값을_검증한다() throws Exception {
		Cookie userCookie = loginCookie(createUser("site"));

		mockMvc.perform(delete("/users/me")
						.cookie(userCookie)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value(containsString("password:")));

		mockMvc.perform(delete("/users/me")
						.cookie(userCookie)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of("password", "   "))))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value(containsString("password:")));
	}

	@Test
	void 프로젝트에_연결된_사용자는_admin_삭제가_충돌난다() throws Exception {
		Cookie adminCookie = loginCookie(createUser("admin"));
		Map<String, String> site = createUser("site");
		Integer siteUserId = readUserIdFromLogin(site);
		insertProjectAssignment(siteUserId);

		mockMvc.perform(delete("/users/{userId}", siteUserId)
				.cookie(adminCookie))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value("연결된 데이터가 있어 사용자를 삭제할 수 없습니다."));
	}

	@Test
	void 프로젝트에_연결된_사용자는_본인탈퇴가_충돌난다() throws Exception {
		Map<String, String> site = createUser("site");
		Cookie siteCookie = loginCookie(site);
		Integer siteUserId = readUserIdFromLogin(site);
		insertProjectAssignment(siteUserId);

		mockMvc.perform(delete("/users/me")
						.cookie(siteCookie)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of("password", site.get("password")))))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value("연결된 데이터가 있어 사용자를 삭제할 수 없습니다."));
	}

	@Test
	void 로그인은_access_token_쿠키를_발급한다() throws Exception {
		Map<String, String> signupRequest = createUser("site");

		MvcResult result = mockMvc.perform(post("/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of(
								"employeeNo", signupRequest.get("employeeNo"),
								"password", signupRequest.get("password")
						))))
				.andExpect(status().isOk())
				.andExpect(header().string(HttpHeaders.SET_COOKIE, allOf(
						containsString("access_token="),
						containsString("Max-Age=3600"),
						containsString("Path=/"),
						containsString("HttpOnly")
				)))
				.andReturn();

		Cookie cookie = result.getResponse().getCookie("access_token");
		assertThat(cookie).isNotNull();
	}

	@Test
	void 로그아웃은_인증쿠키가_없어도_access_token을_만료시킨다() throws Exception {
		mockMvc.perform(post("/auth/logout"))
				.andExpect(status().isOk())
				.andExpect(header().string(HttpHeaders.SET_COOKIE, allOf(
						containsString("access_token="),
						containsString("Max-Age=0"),
						containsString("Path=/"),
						containsString("HttpOnly")
				)))
				.andExpect(jsonPath("$.success").value(true));
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

	private Integer readUserIdFromLogin(Map<String, String> signupRequest) throws Exception {
		MvcResult result = mockMvc.perform(post("/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of(
								"employeeNo", signupRequest.get("employeeNo"),
								"password", signupRequest.get("password")
						))))
				.andExpect(status().isOk())
				.andReturn();

		return readUserId(result);
	}

	private void insertProjectAssignment(Integer userId) {
		Long projectId = jdbcTemplate.queryForObject("""
				INSERT INTO service.projects
					(contract_no, construction_company, project_name, site_location, contract_amount,
					 construction_start_date, construction_end_date, appropriated_amount)
				VALUES (?, '스칼라건설', ?, '서울시 강남구', 100000000,
					'2026-05-01', '2026-12-31', 10000000)
				RETURNING id
				""", Long.class, "CN-" + UUID.randomUUID(), "사용자삭제충돌-" + UUID.randomUUID());

		jdbcTemplate.update("""
				INSERT INTO service.project_user_assignments (project_id, user_id)
				VALUES (?, ?)
				""", projectId, userId);
	}

	private Integer createUserByAdmin(Cookie adminCookie, String employeeNo, String realName, String roleCode) throws Exception {
		MvcResult result = mockMvc.perform(post("/users")
						.cookie(adminCookie)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of(
								"employeeNo", employeeNo,
								"realName", realName,
								"password", "P@ssw0rd123!",
								"roleCode", roleCode
						))))
				.andExpect(status().isCreated())
				.andReturn();

		return readUserId(result);
	}

	private Integer readUserId(MvcResult result) throws Exception {
		return objectMapper.readTree(result.getResponse().getContentAsString())
				.path("data")
				.path("user")
				.path("id")
				.asInt();
	}
}
