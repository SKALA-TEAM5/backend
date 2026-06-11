package com.skala.backend.agent.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skala.backend.agent.client.FastApiAgentClient;
import com.skala.backend.agent.dto.AgentResponses;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AgentRunControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;

    @MockitoBean
    FastApiAgentClient fastApiAgentClient;

    // ─── POST /agents/parse ───────────────────────────────────────────────

    @Test
    void parse_성공_시_usageStatementId와_itemCount를_반환한다() throws Exception {
        Cookie cookie = loginCookie(createUser("admin"));
        int projectId = createProject(cookie);

        when(fastApiAgentClient.parseUsageStatement(anyLong(), anyLong()))
                .thenReturn(new AgentResponses.ParseResult(42L, 15));

        mockMvc.perform(post("/projects/{pid}/agents/parse", projectId)
                        .cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("fileId", 10))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.usageStatementId").value(42))
                .andExpect(jsonPath("$.data.itemCount").value(15));

        verify(fastApiAgentClient).parseUsageStatement((long) projectId, 10L);
    }

    @Test
    void parse_fileId_누락_시_400을_반환한다() throws Exception {
        Cookie cookie = loginCookie(createUser("admin"));
        int projectId = createProject(cookie);

        mockMvc.perform(post("/projects/{pid}/agents/parse", projectId)
                        .cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void parse_미인증_요청은_401을_반환한다() throws Exception {
        Cookie cookie = loginCookie(createUser("admin"));
        int projectId = createProject(cookie);

        mockMvc.perform(post("/projects/{pid}/agents/parse", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("fileId", 10))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void parse_프로젝트_비담당_user는_403을_반환한다() throws Exception {
        Cookie adminCookie = loginCookie(createUser("admin"));
        Cookie outsiderCookie = loginCookie(createUser("user"));
        int projectId = createProject(adminCookie);

        mockMvc.perform(post("/projects/{pid}/agents/parse", projectId)
                        .cookie(outsiderCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("fileId", 10))))
                .andExpect(status().isForbidden());
    }

    @Test
    void parse_FastAPI_장애_시_503을_반환한다() throws Exception {
        Cookie cookie = loginCookie(createUser("admin"));
        int projectId = createProject(cookie);

        doThrow(new RestClientException("connection refused"))
                .when(fastApiAgentClient).parseUsageStatement(anyLong(), anyLong());

        mockMvc.perform(post("/projects/{pid}/agents/parse", projectId)
                        .cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("fileId", 10))))
                .andExpect(status().isServiceUnavailable());
    }

    // ─── POST /agents/validate ────────────────────────────────────────────
    // 비동기 전환 후 202 즉시 반환. 진행 상태는 button-states 폴링으로 확인.
    // 선행 조건 없음. 비활성 조건: safety-doc / link / vision 중 하나라도 running/pending (stale 제외).

    @Test
    void validate_202를_반환한다() throws Exception {
        Cookie cookie = loginCookie(createUser("admin"));
        int projectId = createProject(cookie);
        int statementId = insertStatement(projectId);
        insertLog(projectId, statementId, "classi", "success", "success");

        mockMvc.perform(post("/projects/{pid}/agents/validate", projectId)
                        .cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("usageStatementId", statementId))))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("유효성 검증이 시작되었습니다."));
    }

    @Test
    void validate_classi_없으면_400을_반환한다() throws Exception {
        Cookie cookie = loginCookie(createUser("admin"));
        int projectId = createProject(cookie);
        int statementId = insertStatement(projectId);

        mockMvc.perform(post("/projects/{pid}/agents/validate", projectId)
                        .cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("usageStatementId", statementId))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("parse를 먼저 실행해야 합니다."));
    }

    @Test
    void validate_classi_result_code가_hil이면_400을_반환한다() throws Exception {
        Cookie cookie = loginCookie(createUser("admin"));
        int projectId = createProject(cookie);
        int statementId = insertStatement(projectId);
        insertLog(projectId, statementId, "classi", "success", "hil");

        mockMvc.perform(post("/projects/{pid}/agents/validate", projectId)
                        .cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("usageStatementId", statementId))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("parse를 먼저 실행해야 합니다."));
    }

    @Test
    void validate_실행_중이면_409를_반환한다() throws Exception {
        Cookie cookie = loginCookie(createUser("admin"));
        int projectId = createProject(cookie);
        int statementId = insertStatement(projectId);
        insertLog(projectId, statementId, "classi", "success", "success");
        insertRunningLog(projectId, statementId, "safety-doc", "running");

        mockMvc.perform(post("/projects/{pid}/agents/validate", projectId)
                        .cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("usageStatementId", statementId))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("현재 실행 중입니다."));
    }

    @Test
    void validate_stale_running이면_202를_반환한다() throws Exception {
        Cookie cookie = loginCookie(createUser("admin"));
        int projectId = createProject(cookie);
        int statementId = insertStatement(projectId);
        insertLog(projectId, statementId, "classi", "success", "success");
        insertStaleRunningLog(projectId, statementId, "safety-doc", "running");

        mockMvc.perform(post("/projects/{pid}/agents/validate", projectId)
                        .cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("usageStatementId", statementId))))
                .andExpect(status().isAccepted());
    }

    @Test
    void validate_usageStatementId_누락_시_400을_반환한다() throws Exception {
        Cookie cookie = loginCookie(createUser("admin"));
        int projectId = createProject(cookie);

        mockMvc.perform(post("/projects/{pid}/agents/validate", projectId)
                        .cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void validate_미인증_요청은_401을_반환한다() throws Exception {
        Cookie cookie = loginCookie(createUser("admin"));
        int projectId = createProject(cookie);

        mockMvc.perform(post("/projects/{pid}/agents/validate", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("usageStatementId", 1))))
                .andExpect(status().isUnauthorized());
    }

    // ─── POST /agents/legal ───────────────────────────────────────────────
    // 비동기 전환 후 202 즉시 반환.
    // 선행 조건: safety_doc success+(success|hil), link/vision 존재 시 동일 조건.
    // 비활성 조건: legal running/pending (stale 제외).

    @Test
    void legal_202를_반환한다() throws Exception {
        Cookie cookie = loginCookie(createUser("admin"));
        int projectId = createProject(cookie);
        int statementId = insertStatement(projectId);
        insertLog(projectId, statementId, "safety-doc", "success", "success");

        mockMvc.perform(post("/projects/{pid}/agents/legal", projectId)
                        .cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("usageStatementId", statementId))))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("법령 검증이 시작되었습니다."));
    }

    @Test
    void legal_safety_doc_hil이면_실행_가능하다() throws Exception {
        Cookie cookie = loginCookie(createUser("admin"));
        int projectId = createProject(cookie);
        int statementId = insertStatement(projectId);
        insertLog(projectId, statementId, "safety-doc", "success", "hil");

        mockMvc.perform(post("/projects/{pid}/agents/legal", projectId)
                        .cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("usageStatementId", statementId))))
                .andExpect(status().isAccepted());
    }

    @Test
    void legal_safety_doc_없으면_400을_반환한다() throws Exception {
        Cookie cookie = loginCookie(createUser("admin"));
        int projectId = createProject(cookie);
        int statementId = insertStatement(projectId);

        mockMvc.perform(post("/projects/{pid}/agents/legal", projectId)
                        .cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("usageStatementId", statementId))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("validate를 먼저 실행해야 합니다."));
    }

    @Test
    void legal_safety_doc_result_fail이면_400을_반환한다() throws Exception {
        Cookie cookie = loginCookie(createUser("admin"));
        int projectId = createProject(cookie);
        int statementId = insertStatement(projectId);
        insertLog(projectId, statementId, "safety-doc", "success", "fail");

        mockMvc.perform(post("/projects/{pid}/agents/legal", projectId)
                        .cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("usageStatementId", statementId))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("validate를 먼저 실행해야 합니다."));
    }

    @Test
    void legal_link_존재하고_result_fail이면_400을_반환한다() throws Exception {
        Cookie cookie = loginCookie(createUser("admin"));
        int projectId = createProject(cookie);
        int statementId = insertStatement(projectId);
        insertLog(projectId, statementId, "safety-doc", "success", "success");
        insertLog(projectId, statementId, "link", "success", "fail");

        mockMvc.perform(post("/projects/{pid}/agents/legal", projectId)
                        .cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("usageStatementId", statementId))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("link 검증을 통과해야 합니다."));
    }

    @Test
    void legal_vision_존재하고_result_fail이면_400을_반환한다() throws Exception {
        Cookie cookie = loginCookie(createUser("admin"));
        int projectId = createProject(cookie);
        int statementId = insertStatement(projectId);
        insertLog(projectId, statementId, "safety-doc", "success", "success");
        insertLog(projectId, statementId, "vision", "success", "fail");

        mockMvc.perform(post("/projects/{pid}/agents/legal", projectId)
                        .cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("usageStatementId", statementId))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("vision 검증을 통과해야 합니다."));
    }

    @Test
    void legal_실행_중이면_409를_반환한다() throws Exception {
        Cookie cookie = loginCookie(createUser("admin"));
        int projectId = createProject(cookie);
        int statementId = insertStatement(projectId);
        insertLog(projectId, statementId, "safety-doc", "success", "success");
        insertRunningLog(projectId, statementId, "legal", "running");

        mockMvc.perform(post("/projects/{pid}/agents/legal", projectId)
                        .cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("usageStatementId", statementId))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("현재 실행 중입니다."));
    }

    @Test
    void legal_stale_running이면_202를_반환한다() throws Exception {
        Cookie cookie = loginCookie(createUser("admin"));
        int projectId = createProject(cookie);
        int statementId = insertStatement(projectId);
        insertLog(projectId, statementId, "safety-doc", "success", "success");
        insertStaleRunningLog(projectId, statementId, "legal", "running");

        mockMvc.perform(post("/projects/{pid}/agents/legal", projectId)
                        .cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("usageStatementId", statementId))))
                .andExpect(status().isAccepted());
    }

    @Test
    void legal_usageStatementId_누락_시_400을_반환한다() throws Exception {
        Cookie cookie = loginCookie(createUser("admin"));
        int projectId = createProject(cookie);

        mockMvc.perform(post("/projects/{pid}/agents/legal", projectId)
                        .cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void legal_미인증_요청은_401을_반환한다() throws Exception {
        Cookie cookie = loginCookie(createUser("admin"));
        int projectId = createProject(cookie);

        mockMvc.perform(post("/projects/{pid}/agents/legal", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("usageStatementId", 1))))
                .andExpect(status().isUnauthorized());
    }

    // ─── POST /agents/report ──────────────────────────────────────────────
    // 비동기 전환 후 202 즉시 반환.
    // 선행 조건: legal.status=success AND legal.result_code IN (success, hil).
    // 비활성 조건: report running/pending (stale 제외).

    @Test
    void report_202를_반환한다() throws Exception {
        Cookie cookie = loginCookie(createUser("admin"));
        int projectId = createProject(cookie);
        int statementId = insertStatement(projectId);
        insertLog(projectId, statementId, "legal", "success", "success");

        mockMvc.perform(post("/projects/{pid}/agents/report", projectId)
                        .cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("usageStatementId", statementId))))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("보고서 생성이 시작되었습니다."));
    }

    @Test
    void report_legal_hil이면_실행_가능하다() throws Exception {
        Cookie cookie = loginCookie(createUser("admin"));
        int projectId = createProject(cookie);
        int statementId = insertStatement(projectId);
        insertLog(projectId, statementId, "legal", "success", "hil");

        mockMvc.perform(post("/projects/{pid}/agents/report", projectId)
                        .cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("usageStatementId", statementId))))
                .andExpect(status().isAccepted());
    }

    @Test
    void report_legal_없으면_400을_반환한다() throws Exception {
        Cookie cookie = loginCookie(createUser("admin"));
        int projectId = createProject(cookie);
        int statementId = insertStatement(projectId);

        mockMvc.perform(post("/projects/{pid}/agents/report", projectId)
                        .cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("usageStatementId", statementId))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("legal을 먼저 실행해야 합니다."));
    }

    @Test
    void report_legal_result_fail이면_400을_반환한다() throws Exception {
        Cookie cookie = loginCookie(createUser("admin"));
        int projectId = createProject(cookie);
        int statementId = insertStatement(projectId);
        insertLog(projectId, statementId, "legal", "success", "fail");

        mockMvc.perform(post("/projects/{pid}/agents/report", projectId)
                        .cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("usageStatementId", statementId))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("legal을 먼저 실행해야 합니다."));
    }

    @Test
    void report_실행_중이면_409를_반환한다() throws Exception {
        Cookie cookie = loginCookie(createUser("admin"));
        int projectId = createProject(cookie);
        int statementId = insertStatement(projectId);
        insertLog(projectId, statementId, "legal", "success", "success");
        insertRunningLog(projectId, statementId, "report", "running");

        mockMvc.perform(post("/projects/{pid}/agents/report", projectId)
                        .cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("usageStatementId", statementId))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("현재 실행 중입니다."));
    }

    @Test
    void report_stale_running이면_202를_반환한다() throws Exception {
        Cookie cookie = loginCookie(createUser("admin"));
        int projectId = createProject(cookie);
        int statementId = insertStatement(projectId);
        insertLog(projectId, statementId, "legal", "success", "success");
        insertStaleRunningLog(projectId, statementId, "report", "running");

        mockMvc.perform(post("/projects/{pid}/agents/report", projectId)
                        .cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("usageStatementId", statementId))))
                .andExpect(status().isAccepted());
    }

    @Test
    void report_usageStatementId_누락_시_400을_반환한다() throws Exception {
        Cookie cookie = loginCookie(createUser("admin"));
        int projectId = createProject(cookie);

        mockMvc.perform(post("/projects/{pid}/agents/report", projectId)
                        .cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void report_미인증_요청은_401을_반환한다() throws Exception {
        Cookie cookie = loginCookie(createUser("admin"));
        int projectId = createProject(cookie);

        mockMvc.perform(post("/projects/{pid}/agents/report", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("usageStatementId", 1))))
                .andExpect(status().isUnauthorized());
    }

    // ─── 비동기 디스패치 전 'running' 선기록 (race condition 방지) ──────────────
    // POST 202 직후 statement-level 로그가 'running'으로 가시화되어야,
    // 프론트 첫 button-states 폴링이 "미실행"을 "완료"로 오인하지 않는다.

    @Test
    void validate_202_직후_safety_doc만_running으로_선기록한다() throws Exception {
        Cookie cookie = loginCookie(createUser("admin"));
        int projectId = createProject(cookie);
        int statementId = insertStatement(projectId);
        insertLog(projectId, statementId, "classi", "success", "success");

        mockMvc.perform(post("/projects/{pid}/agents/validate", projectId)
                        .cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("usageStatementId", statementId))))
                .andExpect(status().isAccepted());

        // safety-doc은 항상 실행되므로 선기록 → running
        assertEquals("running", statementLogStatus(statementId, "safety-doc"),
                "safety-doc 로그가 running으로 선기록되어야 한다");
        // link/vision은 FastAPI가 조건부로만 실행하므로 선기록하면 안 됨 (stuck-running 회귀 방지)
        assertEquals(0, statementLogCount(statementId, "link"), "link는 선기록하면 안 된다");
        assertEquals(0, statementLogCount(statementId, "vision"), "vision은 선기록하면 안 된다");
    }

    @Test
    void legal_202_직후_legal을_running으로_선기록한다() throws Exception {
        Cookie cookie = loginCookie(createUser("admin"));
        int projectId = createProject(cookie);
        int statementId = insertStatement(projectId);
        insertLog(projectId, statementId, "safety-doc", "success", "success");

        mockMvc.perform(post("/projects/{pid}/agents/legal", projectId)
                        .cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("usageStatementId", statementId))))
                .andExpect(status().isAccepted());

        assertEquals("running", statementLogStatus(statementId, "legal"),
                "legal 로그가 running으로 선기록되어야 한다");
    }

    @Test
    void legal_재실행_시_기존_success_로그를_running_result_null로_리셋한다() throws Exception {
        Cookie cookie = loginCookie(createUser("admin"));
        int projectId = createProject(cookie);
        int statementId = insertStatement(projectId);
        insertLog(projectId, statementId, "safety-doc", "success", "success");
        // 이전 실행 완료 상태 (재실행 대상)
        insertLog(projectId, statementId, "legal", "success", "hil");

        mockMvc.perform(post("/projects/{pid}/agents/legal", projectId)
                        .cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("usageStatementId", statementId))))
                .andExpect(status().isAccepted());

        // ON CONFLICT DO UPDATE 경로: 단일 행이 running으로 갱신되고 result_code는 NULL로 리셋
        assertEquals(1, statementLogCount(statementId, "legal"), "legal 로그는 단일 행이어야 한다");
        assertEquals("running", statementLogStatus(statementId, "legal"),
                "재실행 시 legal 로그가 running으로 갱신되어야 한다");
        assertNull(statementLogResult(statementId, "legal"),
                "재실행 시 result_code가 NULL로 리셋되어야 한다");
    }

    @Test
    void report_202_직후_report를_running으로_선기록한다() throws Exception {
        Cookie cookie = loginCookie(createUser("admin"));
        int projectId = createProject(cookie);
        int statementId = insertStatement(projectId);
        insertLog(projectId, statementId, "legal", "success", "success");

        mockMvc.perform(post("/projects/{pid}/agents/report", projectId)
                        .cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("usageStatementId", statementId))))
                .andExpect(status().isAccepted());

        assertEquals("running", statementLogStatus(statementId, "report"),
                "report 로그가 running으로 선기록되어야 한다");
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
                                "appropriatedAmount", 10_000_000
                        ))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("project").path("id").asInt();
    }

    private int insertStatement(int projectId) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO service.usage_statements
                    (project_id, report_month, revision_no, document_written_date, cumulative_progress_rate)
                VALUES (?, '2026-05-01', 1, '2026-05-01', 30)
                RETURNING id
                """, Integer.class, projectId);
    }

    /** status=success/fail 인 완료 로그 — result_code 필수 */
    private void insertLog(int projectId, int statementId, String agentTypeCode, String statusCode, String resultCode) {
        jdbcTemplate.update("""
                INSERT INTO service.agent_logs
                    (project_id, usage_statement_id, agent_type_code, status_code, result_code)
                VALUES (?, ?, ?, ?, ?)
                """, projectId, statementId, agentTypeCode, statusCode, resultCode);
    }

    /** 최근 생성된 running/pending 로그 — stale 임계값(15분) 이내이므로 409 유발 */
    private void insertRunningLog(int projectId, int statementId, String agentTypeCode, String statusCode) {
        jdbcTemplate.update("""
                INSERT INTO service.agent_logs
                    (project_id, usage_statement_id, agent_type_code, status_code)
                VALUES (?, ?, ?, ?)
                """, projectId, statementId, agentTypeCode, statusCode);
    }

    /** stale running/pending 로그 — updated_at을 16분 전으로 설정해 stale 임계값 초과 → 재실행 허용 */
    private void insertStaleRunningLog(int projectId, int statementId, String agentTypeCode, String statusCode) {
        jdbcTemplate.update("""
                INSERT INTO service.agent_logs
                    (project_id, usage_statement_id, agent_type_code, status_code, created_at, updated_at)
                VALUES (?, ?, ?, ?, NOW() - INTERVAL '16 minutes', NOW() - INTERVAL '16 minutes')
                """, projectId, statementId, agentTypeCode, statusCode);
    }

    /** statement-level(item_id IS NULL) 로그의 status_code 조회 — 없으면 null */
    private String statementLogStatus(int statementId, String agentTypeCode) {
        return jdbcTemplate.query("""
                SELECT status_code FROM service.agent_logs
                WHERE usage_statement_id = ? AND agent_type_code = ? AND usage_statement_item_id IS NULL
                """, rs -> rs.next() ? rs.getString(1) : null, statementId, agentTypeCode);
    }

    /** statement-level(item_id IS NULL) 로그의 result_code 조회 — 없으면 null */
    private String statementLogResult(int statementId, String agentTypeCode) {
        return jdbcTemplate.query("""
                SELECT result_code FROM service.agent_logs
                WHERE usage_statement_id = ? AND agent_type_code = ? AND usage_statement_item_id IS NULL
                """, rs -> rs.next() ? rs.getString(1) : null, statementId, agentTypeCode);
    }

    /** statement-level(item_id IS NULL) 로그 행 수 */
    private int statementLogCount(int statementId, String agentTypeCode) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM service.agent_logs
                WHERE usage_statement_id = ? AND agent_type_code = ? AND usage_statement_item_id IS NULL
                """, Integer.class, statementId, agentTypeCode);
        return count != null ? count : 0;
    }
}
