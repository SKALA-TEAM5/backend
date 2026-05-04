package com.skala.backend.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
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

	@Test
	void 회원가입에_성공하면_사용자를_생성한다() throws Exception {
		Map<String, String> request = signupRequest();

		mockMvc.perform(post("/auth/signup")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.user.employeeNo").value(request.get("employeeNo")))
				.andExpect(jsonPath("$.data.user.realName").value(request.get("realName")))
				.andExpect(jsonPath("$.data.user.roleCode").value(request.get("roleCode")))
				.andExpect(jsonPath("$.data.user.id").isNumber())
				.andExpect(jsonPath("$.data.user.createdAt").exists())
				.andExpect(jsonPath("$.data.user.updatedAt").exists());
	}

	@Test
	void 회원가입은_수정된_권한값을_허용한다() throws Exception {
		for (String roleCode : new String[] {"admin", "hq", "site", "agent"}) {
			Map<String, String> request = signupRequest(roleCode);

			mockMvc.perform(post("/auth/signup")
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(request)))
					.andExpect(status().isCreated())
					.andExpect(jsonPath("$.data.user.roleCode").value(roleCode));
		}
	}

	@Test
	void 이미_존재하는_사번으로_회원가입하면_409를_반환한다() throws Exception {
		Map<String, String> request = signupRequest();
		String content = objectMapper.writeValueAsString(request);

		mockMvc.perform(post("/auth/signup")
						.contentType(MediaType.APPLICATION_JSON)
						.content(content))
				.andExpect(status().isCreated());

		mockMvc.perform(post("/auth/signup")
						.contentType(MediaType.APPLICATION_JSON)
						.content(content))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.message").value("이미 존재하는 사번입니다."));
	}

	@Test
	void 회원가입_요청값이_유효하지_않으면_400을_반환한다() throws Exception {
		Map<String, String> request = Map.of(
				"employeeNo", "EMP-" + UUID.randomUUID(),
				"realName", "홍길동",
				"password", "short",
				"roleCode", "site"
		);

		mockMvc.perform(post("/auth/signup")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.message").value(startsWith("password:")));
	}

	@Test
	void 로그인에_성공하면_사용자_정보를_반환한다() throws Exception {
		Map<String, String> signupRequest = signupRequest();
		mockMvc.perform(post("/auth/signup")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(signupRequest)))
				.andExpect(status().isCreated());

		Map<String, String> loginRequest = Map.of(
				"employeeNo", signupRequest.get("employeeNo"),
				"password", signupRequest.get("password")
		);

		mockMvc.perform(post("/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(loginRequest)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.user.employeeNo").value(signupRequest.get("employeeNo")))
				.andExpect(jsonPath("$.data.user.realName").value(signupRequest.get("realName")))
				.andExpect(jsonPath("$.data.user.roleCode").value(signupRequest.get("roleCode")));
	}

	@Test
	void 비밀번호가_일치하지_않으면_로그인에_실패한다() throws Exception {
		Map<String, String> signupRequest = signupRequest();
		mockMvc.perform(post("/auth/signup")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(signupRequest)))
				.andExpect(status().isCreated());

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
						containsString("Secure"),
						containsString("SameSite=Lax")
				)))
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data").doesNotExist())
				.andExpect(jsonPath("$.message").value("로그아웃에 성공했습니다."));
	}

	private Map<String, String> signupRequest() {
		return signupRequest("site");
	}

	private Map<String, String> signupRequest(String roleCode) {
		return Map.of(
				"employeeNo", "EMP-" + UUID.randomUUID(),
				"realName", "홍길동",
				"password", "P@ssw0rd123!",
				"roleCode", roleCode
		);
	}
}
