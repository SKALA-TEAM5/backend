package com.skala.backend.user.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
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

	@Test
	void admin은_일반_user_계정을_CRUD_할_수_있다() throws Exception {
		Cookie adminCookie = loginCookie(createUser("admin"));
		String employeeNo = "EMP-" + UUID.randomUUID();

		MvcResult createResult = mockMvc.perform(post("/users")
						.cookie(adminCookie)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of(
								"employeeNo", employeeNo,
								"realName", "김담당",
								"password", "P@ssw0rd123!",
								"roleCode", "user"
						))))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.user.employeeNo").value(employeeNo))
				.andExpect(jsonPath("$.data.user.realName").value("김담당"))
				.andExpect(jsonPath("$.data.user.roleCode").value("user"))
				.andExpect(jsonPath("$.data.user.password").doesNotExist())
				.andExpect(jsonPath("$.data.user.passwordHash").doesNotExist())
				.andReturn();

		Integer userId = readUserId(createResult);

		mockMvc.perform(get("/users")
						.cookie(adminCookie)
						.param("roleCode", "user")
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
	void admin은_사용자를_생성할_때_권한을_부여할_수_있다() throws Exception {
		Cookie adminCookie = loginCookie(createUser("admin"));

		mockMvc.perform(post("/users")
						.cookie(adminCookie)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of(
								"employeeNo", "EMP-" + UUID.randomUUID(),
								"realName", "권한부여대상",
								"password", "P@ssw0rd123!",
								"roleCode", "agent"
						))))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.user.roleCode").value("agent"));

		mockMvc.perform(post("/users")
						.cookie(adminCookie)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of(
								"employeeNo", "EMP-" + UUID.randomUUID(),
								"realName", "관리자부여대상",
								"password", "P@ssw0rd123!",
								"roleCode", "admin"
						))))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.user.roleCode").value("admin"));
	}

	@Test
	void admin은_사용자_목록을_권한과_키워드로_필터링할_수_있다() throws Exception {
		Cookie adminCookie = loginCookie(createUser("admin"));
		String targetEmployeeNo = "EMP-" + UUID.randomUUID();

		createUserByAdmin(adminCookie, targetEmployeeNo, "검색대상", "user");
		createUserByAdmin(adminCookie, "EMP-" + UUID.randomUUID(), "검색대상", "agent");
		createUserByAdmin(adminCookie, "EMP-" + UUID.randomUUID(), "다른이름", "user");

		mockMvc.perform(get("/users")
						.cookie(adminCookie)
						.param("roleCode", "user")
						.param("keyword", "검색대상"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items", hasSize(1)))
				.andExpect(jsonPath("$.data.items[0].employeeNo").value(targetEmployeeNo))
				.andExpect(jsonPath("$.data.items[0].roleCode").value("user"));

		mockMvc.perform(get("/users")
						.cookie(adminCookie)
						.param("keyword", targetEmployeeNo.substring(0, 8)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items", hasSize(1)))
				.andExpect(jsonPath("$.data.items[0].employeeNo").value(targetEmployeeNo));
	}

	@Test
	void admin은_사용자_비밀번호를_변경할_수_있고_기존_비밀번호는_더_이상_동작하지_않는다() throws Exception {
		Cookie adminCookie = loginCookie(createUser("admin"));
		String employeeNo = "EMP-" + UUID.randomUUID();
		String oldPassword = "P@ssw0rd123!";
		String newPassword = "N3wP@ssw0rd!";
		Integer userId = createUserByAdmin(adminCookie, employeeNo, "비밀번호변경대상", "user");

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
	void admin_사용자_생성은_사번_중복과_유효하지_않은_요청을_거부한다() throws Exception {
		Cookie adminCookie = loginCookie(createUser("admin"));
		String employeeNo = "EMP-" + UUID.randomUUID();

		createUserByAdmin(adminCookie, employeeNo, "중복대상", "user");

		mockMvc.perform(post("/users")
						.cookie(adminCookie)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of(
								"employeeNo", employeeNo,
								"realName", "중복대상2",
								"password", "P@ssw0rd123!",
								"roleCode", "user"
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
								"roleCode", "user"
						))))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value(containsString("password:")));
	}

	@Test
	void admin_사용자_수정은_빈_본문과_존재하지_않는_사용자를_거부한다() throws Exception {
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
	void admin_사용자_삭제는_존재하지_않는_사용자를_거부한다() throws Exception {
		Cookie adminCookie = loginCookie(createUser("admin"));

		mockMvc.perform(delete("/users/{userId}", 999_999_999L)
						.cookie(adminCookie))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.message").value("사용자를 찾을 수 없습니다."));
	}

	@Test
	void admin이_아니면_사용자_관리_API에_접근할_수_없다() throws Exception {
		Cookie userCookie = loginCookie(createUser("user"));

		mockMvc.perform(get("/users").cookie(userCookie))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.message").value("권한이 없습니다."));

		mockMvc.perform(post("/users")
						.cookie(userCookie)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of(
								"employeeNo", "EMP-" + UUID.randomUUID(),
								"realName", "권한없음",
								"password", "P@ssw0rd123!",
								"roleCode", "user"
						))))
				.andExpect(status().isForbidden());
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
	}

	@Test
	void 로그인_사용자는_내_프로필을_조회하고_탈퇴할_수_있다() throws Exception {
		Map<String, String> signupRequest = createUser("user");
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
	void 로그인은_access_token_쿠키를_발급한다() throws Exception {
		Map<String, String> signupRequest = createUser("user");

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
