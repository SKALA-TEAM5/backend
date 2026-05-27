package com.skala.backend.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skala.backend.user.domain.RoleCode;
import com.skala.backend.user.domain.User;
import com.skala.backend.user.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthControllerTest {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	ObjectMapper objectMapper;

	@Autowired
	UserRepository userRepository;

	@Autowired
	PasswordEncoder passwordEncoder;

	@Test
	void 공개_회원가입_API는_제공하지_않는다() throws Exception {
		mockMvc.perform(post("/auth/signup")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of(
								"employeeNo", "EMP-" + UUID.randomUUID(),
								"realName", "홍길동",
								"password", "P@ssw0rd123!",
								"roleCode", "user"
						))))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void 로그인에_성공하면_사용자_정보와_인증쿠키를_반환한다() throws Exception {
		Map<String, String> signupRequest = createUser("user");

		Map<String, String> loginRequest = Map.of(
				"employeeNo", signupRequest.get("employeeNo"),
				"password", signupRequest.get("password")
		);

		var result = mockMvc.perform(post("/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(loginRequest)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.user.employeeNo").value(signupRequest.get("employeeNo")))
				.andExpect(jsonPath("$.data.user.realName").value(signupRequest.get("realName")))
				.andExpect(jsonPath("$.data.user.roleCode").value(signupRequest.get("roleCode")))
				.andReturn();

		Cookie accessToken = result.getResponse().getCookie("access_token");
		Cookie refreshToken = result.getResponse().getCookie("refresh_token");
		assertThat(accessToken).isNotNull();
		assertThat(accessToken.getValue()).contains(".");
		assertThat(refreshToken).isNotNull();
		assertThat(refreshToken.getValue()).doesNotContain(".");
	}

	@Test
	void 비밀번호가_일치하지_않으면_로그인에_실패한다() throws Exception {
		Map<String, String> signupRequest = createUser("user");

		Map<String, String> loginRequest = Map.of(
				"employeeNo", signupRequest.get("employeeNo"),
				"password", "wrong-password"
		);

		mockMvc.perform(post("/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(loginRequest)))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.message").value("사번 또는 비밀번호가 일치하지 않습니다."));
	}

	@Test
	void 존재하지_않는_사번이면_로그인에_실패한다() throws Exception {
		Map<String, String> loginRequest = Map.of(
				"employeeNo", "EMP-" + UUID.randomUUID(),
				"password", "P@ssw0rd123!"
		);

		mockMvc.perform(post("/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(loginRequest)))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.message").value("사번 또는 비밀번호가 일치하지 않습니다."));
	}

	@Test
	void 로그아웃에_성공하면_인증_쿠키를_만료한다() throws Exception {
		mockMvc.perform(post("/auth/logout"))
				.andExpect(status().isOk())
				.andExpect(header().string(HttpHeaders.SET_COOKIE, allOf(
						containsString("access_token="),
						containsString("Max-Age=0"),
						containsString("Path=/"),
						containsString("HttpOnly"),
						containsString("SameSite=Lax")
				)))
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data").doesNotExist())
				.andExpect(jsonPath("$.message").value("로그아웃에 성공했습니다."));
	}

	@Test
	void 리프레시_토큰으로_토큰을_재발급하고_기존_리프레시토큰은_재사용할_수_없다() throws Exception {
		Map<String, String> signupRequest = createUser("user");

		var loginResult = mockMvc.perform(post("/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of(
								"employeeNo", signupRequest.get("employeeNo"),
								"password", signupRequest.get("password")
						))))
				.andExpect(status().isOk())
				.andReturn();

		Cookie oldRefreshToken = loginResult.getResponse().getCookie("refresh_token");
		assertThat(oldRefreshToken).isNotNull();

		var refreshResult = mockMvc.perform(post("/auth/refresh")
						.cookie(oldRefreshToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.user.employeeNo").value(signupRequest.get("employeeNo")))
				.andReturn();

		assertThat(refreshResult.getResponse().getCookie("access_token")).isNotNull();
		assertThat(refreshResult.getResponse().getCookie("refresh_token")).isNotNull();

		mockMvc.perform(post("/auth/refresh")
						.cookie(oldRefreshToken))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.message").value("유효하지 않은 인증 정보입니다."));
	}

	@Test
	void 로그아웃한_리프레시토큰은_재사용할_수_없다() throws Exception {
		Map<String, String> signupRequest = createUser("user");

		var loginResult = mockMvc.perform(post("/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of(
								"employeeNo", signupRequest.get("employeeNo"),
								"password", signupRequest.get("password")
						))))
				.andExpect(status().isOk())
				.andReturn();

		Cookie refreshToken = loginResult.getResponse().getCookie("refresh_token");
		assertThat(refreshToken).isNotNull();

		mockMvc.perform(post("/auth/logout")
						.cookie(refreshToken))
				.andExpect(status().isOk());

		mockMvc.perform(post("/auth/refresh")
						.cookie(refreshToken))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.message").value("유효하지 않은 인증 정보입니다."));
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
}
