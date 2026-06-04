package com.skala.backend.agent.controller;

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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AgentButtonStatesControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;

    // ─── GET /agents/button-states ────────────────────────────────────────

    @Test
    void 로그_없으면_validate만_활성화되고_legal_report는_비활성화된다() throws Exception {
        Cookie cookie = loginCookie(createUser("admin"));
        int projectId = createProject(cookie);
        int statementId = insertStatement(projectId);

        mockMvc.perform(get("/projects/{pid}/agents/button-states", projectId)
                        .cookie(cookie)
                        .param("usageStatementId", String.valueOf(statementId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.validate.enabled").value(true))
                .andExpect(jsonPath("$.data.validate.reason").isEmpty())
                .andExpect(jsonPath("$.data.legal.enabled").value(false))
                .andExpect(jsonPath("$.data.legal.reason").value("validate를 먼저 실행해야 합니다."))
                .andExpect(jsonPath("$.data.report.enabled").value(false))
                .andExpect(jsonPath("$.data.report.reason").value("validate를 먼저 실행해야 합니다."));
    }

    @Test
    void safety_doc_완료되면_legal도_활성화된다() throws Exception {
        Cookie cookie = loginCookie(createUser("admin"));
        int projectId = createProject(cookie);
        int statementId = insertStatement(projectId);
        insertStatementLog(projectId, statementId, "safety-doc", "success");

        mockMvc.perform(get("/projects/{pid}/agents/button-states", projectId)
                        .cookie(cookie)
                        .param("usageStatementId", String.valueOf(statementId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.validate.enabled").value(true))
                .andExpect(jsonPath("$.data.legal.enabled").value(true))
                .andExpect(jsonPath("$.data.legal.reason").isEmpty())
                .andExpect(jsonPath("$.data.report.enabled").value(false));
    }

    @Test
    void safety_doc_fail이어도_legal은_활성화된다() throws Exception {
        Cookie cookie = loginCookie(createUser("admin"));
        int projectId = createProject(cookie);
        int statementId = insertStatement(projectId);
        insertStatementLog(projectId, statementId, "safety-doc", "fail");

        mockMvc.perform(get("/projects/{pid}/agents/button-states", projectId)
                        .cookie(cookie)
                        .param("usageStatementId", String.valueOf(statementId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.legal.enabled").value(true));
    }

    @Test
    void legal_완료되면_report도_활성화된다() throws Exception {
        Cookie cookie = loginCookie(createUser("admin"));
        int projectId = createProject(cookie);
        int statementId = insertStatement(projectId);
        insertStatementLog(projectId, statementId, "safety-doc", "success");
        insertStatementLog(projectId, statementId, "legal", "success");

        mockMvc.perform(get("/projects/{pid}/agents/button-states", projectId)
                        .cookie(cookie)
                        .param("usageStatementId", String.valueOf(statementId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.validate.enabled").value(true))
                .andExpect(jsonPath("$.data.legal.enabled").value(true))
                .andExpect(jsonPath("$.data.report.enabled").value(true))
                .andExpect(jsonPath("$.data.report.reason").isEmpty());
    }

    @Test
    void legal_fail이어도_report는_활성화된다() throws Exception {
        Cookie cookie = loginCookie(createUser("admin"));
        int projectId = createProject(cookie);
        int statementId = insertStatement(projectId);
        insertStatementLog(projectId, statementId, "safety-doc", "success");
        insertStatementLog(projectId, statementId, "legal", "fail");

        mockMvc.perform(get("/projects/{pid}/agents/button-states", projectId)
                        .cookie(cookie)
                        .param("usageStatementId", String.valueOf(statementId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.report.enabled").value(true));
    }

    @Test
    void safety_doc_running이면_validate와_legal이_비활성화된다() throws Exception {
        Cookie cookie = loginCookie(createUser("admin"));
        int projectId = createProject(cookie);
        int statementId = insertStatement(projectId);
        insertStatementLog(projectId, statementId, "safety-doc", "running");

        mockMvc.perform(get("/projects/{pid}/agents/button-states", projectId)
                        .cookie(cookie)
                        .param("usageStatementId", String.valueOf(statementId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.validate.enabled").value(false))
                .andExpect(jsonPath("$.data.validate.reason").value("현재 실행 중입니다."))
                .andExpect(jsonPath("$.data.legal.enabled").value(false))
                .andExpect(jsonPath("$.data.legal.reason").value("현재 실행 중입니다."));
    }

    @Test
    void safety_doc_pending이면_validate와_legal이_비활성화된다() throws Exception {
        Cookie cookie = loginCookie(createUser("admin"));
        int projectId = createProject(cookie);
        int statementId = insertStatement(projectId);
        insertStatementLog(projectId, statementId, "safety-doc", "pending");

        mockMvc.perform(get("/projects/{pid}/agents/button-states", projectId)
                        .cookie(cookie)
                        .param("usageStatementId", String.valueOf(statementId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.validate.enabled").value(false))
                .andExpect(jsonPath("$.data.legal.enabled").value(false));
    }

    @Test
    void legal_running이면_legal과_report가_비활성화된다() throws Exception {
        Cookie cookie = loginCookie(createUser("admin"));
        int projectId = createProject(cookie);
        int statementId = insertStatement(projectId);
        insertStatementLog(projectId, statementId, "safety-doc", "success");
        insertStatementLog(projectId, statementId, "legal", "running");

        mockMvc.perform(get("/projects/{pid}/agents/button-states", projectId)
                        .cookie(cookie)
                        .param("usageStatementId", String.valueOf(statementId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.validate.enabled").value(true))
                .andExpect(jsonPath("$.data.legal.enabled").value(false))
                .andExpect(jsonPath("$.data.legal.reason").value("현재 실행 중입니다."))
                .andExpect(jsonPath("$.data.report.enabled").value(false))
                .andExpect(jsonPath("$.data.report.reason").value("현재 실행 중입니다."));
    }

    @Test
    void report_running이면_report만_비활성화된다() throws Exception {
        Cookie cookie = loginCookie(createUser("admin"));
        int projectId = createProject(cookie);
        int statementId = insertStatement(projectId);
        insertStatementLog(projectId, statementId, "safety-doc", "success");
        insertStatementLog(projectId, statementId, "legal", "success");
        insertStatementLog(projectId, statementId, "report", "running");

        mockMvc.perform(get("/projects/{pid}/agents/button-states", projectId)
                        .cookie(cookie)
                        .param("usageStatementId", String.valueOf(statementId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.validate.enabled").value(true))
                .andExpect(jsonPath("$.data.legal.enabled").value(true))
                .andExpect(jsonPath("$.data.report.enabled").value(false))
                .andExpect(jsonPath("$.data.report.reason").value("현재 실행 중입니다."));
    }

    @Test
    void usageStatementId_누락_시_400을_반환한다() throws Exception {
        Cookie cookie = loginCookie(createUser("admin"));
        int projectId = createProject(cookie);

        mockMvc.perform(get("/projects/{pid}/agents/button-states", projectId)
                        .cookie(cookie))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 미인증_요청은_401을_반환한다() throws Exception {
        Cookie cookie = loginCookie(createUser("admin"));
        int projectId = createProject(cookie);
        int statementId = insertStatement(projectId);

        mockMvc.perform(get("/projects/{pid}/agents/button-states", projectId)
                        .param("usageStatementId", String.valueOf(statementId)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 비담당자_user는_403을_반환한다() throws Exception {
        Cookie adminCookie = loginCookie(createUser("admin"));
        Cookie outsiderCookie = loginCookie(createUser("user"));
        int projectId = createProject(adminCookie);
        int statementId = insertStatement(projectId);

        mockMvc.perform(get("/projects/{pid}/agents/button-states", projectId)
                        .cookie(outsiderCookie)
                        .param("usageStatementId", String.valueOf(statementId)))
                .andExpect(status().isForbidden());
    }

    @Test
    void 담당자_user는_버튼_상태를_조회할_수_있다() throws Exception {
        Cookie adminCookie = loginCookie(createUser("admin"));
        Map<String, String> user = createUser("user");
        Cookie userCookie = loginCookie(user);
        int userId = readUserId(user);
        int projectId = createProject(adminCookie);
        assign(adminCookie, projectId, userId);
        int statementId = insertStatement(projectId);

        mockMvc.perform(get("/projects/{pid}/agents/button-states", projectId)
                        .cookie(userCookie)
                        .param("usageStatementId", String.valueOf(statementId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.validate.enabled").value(true));
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
                                "appropriatedAmount", 10_000_000
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
                VALUES (?, '2026-05-01', 1, '2026-05-01', 30)
                RETURNING id
                """, Integer.class, projectId);
    }

    private void insertStatementLog(int projectId, int statementId, String agentTypeCode, String statusCode) {
        jdbcTemplate.update("""
                INSERT INTO service.agent_logs
                    (project_id, usage_statement_id, agent_type_code, status_code)
                VALUES (?, ?, ?, ?)
                """, projectId, statementId, agentTypeCode, statusCode);
    }
}
