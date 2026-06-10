package com.skala.backend.global.config;

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

import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 미등록 경로 요청 시 Spring Security 에러 디스패치 과정에서
 * 401이 아닌 404를 반환하는지 검증한다.
 *
 * 배경: JwtAuthenticationFilter는 OncePerRequestFilter로, 에러 디스패치 시
 * 재실행되지 않아 SecurityContext가 비어 401이 반환되던 문제.
 * SecurityConfig에 /error 경로를 permitAll()로 추가하여 수정.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SecurityErrorPathTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;

    @Test
    void 인증된_사용자가_미등록_경로를_요청하면_401이_아닌_404를_반환한다() throws Exception {
        Cookie cookie = loginCookie(createUser("admin"));

        mockMvc.perform(get("/projects/1/usage-statements/1/line-items/1/this-does-not-exist")
                        .cookie(cookie))
                .andExpect(status().isNotFound());
    }

    @Test
    void 미인증_사용자가_미등록_경로를_요청하면_401을_반환한다() throws Exception {
        mockMvc.perform(get("/projects/1/usage-statements/1/line-items/1/this-does-not-exist"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 인증된_사용자가_완전히_다른_미등록_경로를_요청해도_404를_반환한다() throws Exception {
        Cookie cookie = loginCookie(createUser("user"));

        mockMvc.perform(get("/completely/unknown/path/xyz")
                        .cookie(cookie))
                .andExpect(status().isNotFound());
    }

    // ─── fixtures ─────────────────────────────────────────────────────────

    private Map<String, String> createUser(String roleCode) {
        Map<String, String> credentials = Map.of(
                "employeeNo", "EMP-" + UUID.randomUUID(),
                "realName", "테스트유저",
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
}
