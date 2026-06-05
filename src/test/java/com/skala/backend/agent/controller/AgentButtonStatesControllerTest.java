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

    // ─── validate 버튼 ────────────────────────────────────────────────────
    // 선행 조건: classi.status=success AND classi.result_code=success
    // 비활성 조건: vision / link / safety_doc 중 하나라도 running/pending

    @Test
    void classi_로그_없으면_validate_비활성() throws Exception {
        Cookie cookie = loginCookie(createUser("admin"));
        int projectId = createProject(cookie);
        int sid = insertStatement(projectId);

        mockMvc.perform(get("/projects/{pid}/agents/button-states", projectId)
                        .cookie(cookie)
                        .param("usageStatementId", String.valueOf(sid)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.validate.enabled").value(false))
                .andExpect(jsonPath("$.data.validate.reason").value("parse를 먼저 실행해야 합니다."));
    }

    @Test
    void classi_result_code가_hil이면_validate_비활성() throws Exception {
        Cookie cookie = loginCookie(createUser("admin"));
        int projectId = createProject(cookie);
        int sid = insertStatement(projectId);
        insertLog(projectId, sid, "classi", "success", "hil");

        mockMvc.perform(get("/projects/{pid}/agents/button-states", projectId)
                        .cookie(cookie)
                        .param("usageStatementId", String.valueOf(sid)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.validate.enabled").value(false))
                .andExpect(jsonPath("$.data.validate.reason").value("parse를 먼저 실행해야 합니다."));
    }

    @Test
    void classi_success_success이면_validate_활성() throws Exception {
        Cookie cookie = loginCookie(createUser("admin"));
        int projectId = createProject(cookie);
        int sid = insertStatement(projectId);
        insertLog(projectId, sid, "classi", "success", "success");

        mockMvc.perform(get("/projects/{pid}/agents/button-states", projectId)
                        .cookie(cookie)
                        .param("usageStatementId", String.valueOf(sid)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.validate.enabled").value(true))
                .andExpect(jsonPath("$.data.validate.reason").isEmpty());
    }

    @Test
    void safety_doc_running이면_validate_비활성() throws Exception {
        Cookie cookie = loginCookie(createUser("admin"));
        int projectId = createProject(cookie);
        int sid = insertStatement(projectId);
        insertLog(projectId, sid, "classi", "success", "success");
        insertRunningLog(projectId, sid, "safety-doc", "running");

        mockMvc.perform(get("/projects/{pid}/agents/button-states", projectId)
                        .cookie(cookie)
                        .param("usageStatementId", String.valueOf(sid)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.validate.enabled").value(false))
                .andExpect(jsonPath("$.data.validate.reason").value("현재 실행 중입니다."));
    }

    @Test
    void link_running이면_validate_비활성() throws Exception {
        Cookie cookie = loginCookie(createUser("admin"));
        int projectId = createProject(cookie);
        int sid = insertStatement(projectId);
        insertLog(projectId, sid, "classi", "success", "success");
        insertRunningLog(projectId, sid, "link", "running");

        mockMvc.perform(get("/projects/{pid}/agents/button-states", projectId)
                        .cookie(cookie)
                        .param("usageStatementId", String.valueOf(sid)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.validate.enabled").value(false))
                .andExpect(jsonPath("$.data.validate.reason").value("현재 실행 중입니다."));
    }

    @Test
    void vision_pending이면_validate_비활성() throws Exception {
        Cookie cookie = loginCookie(createUser("admin"));
        int projectId = createProject(cookie);
        int sid = insertStatement(projectId);
        insertLog(projectId, sid, "classi", "success", "success");
        insertRunningLog(projectId, sid, "vision", "pending");

        mockMvc.perform(get("/projects/{pid}/agents/button-states", projectId)
                        .cookie(cookie)
                        .param("usageStatementId", String.valueOf(sid)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.validate.enabled").value(false))
                .andExpect(jsonPath("$.data.validate.reason").value("현재 실행 중입니다."));
    }

    // ─── legal 버튼 ───────────────────────────────────────────────────────
    // 선행 조건: safety_doc success+(success|hil)
    //           link 존재 시 → success+(success|hil) 충족 필요
    //           vision 존재 시 → success+(success|hil) 충족 필요
    // 비활성 조건: legal running/pending

    @Test
    void safety_doc_없으면_legal_비활성() throws Exception {
        Cookie cookie = loginCookie(createUser("admin"));
        int projectId = createProject(cookie);
        int sid = insertStatement(projectId);
        insertLog(projectId, sid, "classi", "success", "success");

        mockMvc.perform(get("/projects/{pid}/agents/button-states", projectId)
                        .cookie(cookie)
                        .param("usageStatementId", String.valueOf(sid)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.legal.enabled").value(false))
                .andExpect(jsonPath("$.data.legal.reason").value("validate를 먼저 실행해야 합니다."));
    }

    @Test
    void safety_doc_success_fail이면_legal_비활성() throws Exception {
        Cookie cookie = loginCookie(createUser("admin"));
        int projectId = createProject(cookie);
        int sid = insertStatement(projectId);
        insertLog(projectId, sid, "classi", "success", "success");
        insertLog(projectId, sid, "safety-doc", "success", "fail");

        mockMvc.perform(get("/projects/{pid}/agents/button-states", projectId)
                        .cookie(cookie)
                        .param("usageStatementId", String.valueOf(sid)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.legal.enabled").value(false))
                .andExpect(jsonPath("$.data.legal.reason").value("validate를 먼저 실행해야 합니다."));
    }

    @Test
    void safety_doc_success_hil이면_legal_활성() throws Exception {
        Cookie cookie = loginCookie(createUser("admin"));
        int projectId = createProject(cookie);
        int sid = insertStatement(projectId);
        insertLog(projectId, sid, "classi", "success", "success");
        insertLog(projectId, sid, "safety-doc", "success", "hil");

        mockMvc.perform(get("/projects/{pid}/agents/button-states", projectId)
                        .cookie(cookie)
                        .param("usageStatementId", String.valueOf(sid)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.legal.enabled").value(true))
                .andExpect(jsonPath("$.data.legal.reason").isEmpty());
    }

    @Test
    void safety_doc_success_success이면_legal_활성() throws Exception {
        Cookie cookie = loginCookie(createUser("admin"));
        int projectId = createProject(cookie);
        int sid = insertStatement(projectId);
        insertLog(projectId, sid, "classi", "success", "success");
        insertLog(projectId, sid, "safety-doc", "success", "success");

        mockMvc.perform(get("/projects/{pid}/agents/button-states", projectId)
                        .cookie(cookie)
                        .param("usageStatementId", String.valueOf(sid)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.legal.enabled").value(true));
    }

    @Test
    void link_없으면_legal_활성() throws Exception {
        Cookie cookie = loginCookie(createUser("admin"));
        int projectId = createProject(cookie);
        int sid = insertStatement(projectId);
        insertLog(projectId, sid, "classi", "success", "success");
        insertLog(projectId, sid, "safety-doc", "success", "success");

        mockMvc.perform(get("/projects/{pid}/agents/button-states", projectId)
                        .cookie(cookie)
                        .param("usageStatementId", String.valueOf(sid)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.legal.enabled").value(true));
    }

    @Test
    void link_success_success이면_legal_활성() throws Exception {
        Cookie cookie = loginCookie(createUser("admin"));
        int projectId = createProject(cookie);
        int sid = insertStatement(projectId);
        insertLog(projectId, sid, "classi", "success", "success");
        insertLog(projectId, sid, "safety-doc", "success", "success");
        insertLog(projectId, sid, "link", "success", "success");

        mockMvc.perform(get("/projects/{pid}/agents/button-states", projectId)
                        .cookie(cookie)
                        .param("usageStatementId", String.valueOf(sid)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.legal.enabled").value(true));
    }

    @Test
    void link_success_hil이면_legal_활성() throws Exception {
        Cookie cookie = loginCookie(createUser("admin"));
        int projectId = createProject(cookie);
        int sid = insertStatement(projectId);
        insertLog(projectId, sid, "classi", "success", "success");
        insertLog(projectId, sid, "safety-doc", "success", "success");
        insertLog(projectId, sid, "link", "success", "hil");

        mockMvc.perform(get("/projects/{pid}/agents/button-states", projectId)
                        .cookie(cookie)
                        .param("usageStatementId", String.valueOf(sid)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.legal.enabled").value(true));
    }

    @Test
    void link_success_fail이면_legal_비활성() throws Exception {
        Cookie cookie = loginCookie(createUser("admin"));
        int projectId = createProject(cookie);
        int sid = insertStatement(projectId);
        insertLog(projectId, sid, "classi", "success", "success");
        insertLog(projectId, sid, "safety-doc", "success", "success");
        insertLog(projectId, sid, "link", "success", "fail");

        mockMvc.perform(get("/projects/{pid}/agents/button-states", projectId)
                        .cookie(cookie)
                        .param("usageStatementId", String.valueOf(sid)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.legal.enabled").value(false))
                .andExpect(jsonPath("$.data.legal.reason").value("link 또는 vision 검증을 통과해야 합니다."));
    }

    @Test
    void vision_없으면_legal_활성() throws Exception {
        Cookie cookie = loginCookie(createUser("admin"));
        int projectId = createProject(cookie);
        int sid = insertStatement(projectId);
        insertLog(projectId, sid, "classi", "success", "success");
        insertLog(projectId, sid, "safety-doc", "success", "success");
        insertLog(projectId, sid, "link", "success", "success");

        mockMvc.perform(get("/projects/{pid}/agents/button-states", projectId)
                        .cookie(cookie)
                        .param("usageStatementId", String.valueOf(sid)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.legal.enabled").value(true));
    }

    @Test
    void vision_success_fail이면_legal_비활성() throws Exception {
        Cookie cookie = loginCookie(createUser("admin"));
        int projectId = createProject(cookie);
        int sid = insertStatement(projectId);
        insertLog(projectId, sid, "classi", "success", "success");
        insertLog(projectId, sid, "safety-doc", "success", "success");
        insertLog(projectId, sid, "vision", "success", "fail");

        mockMvc.perform(get("/projects/{pid}/agents/button-states", projectId)
                        .cookie(cookie)
                        .param("usageStatementId", String.valueOf(sid)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.legal.enabled").value(false))
                .andExpect(jsonPath("$.data.legal.reason").value("link 또는 vision 검증을 통과해야 합니다."));
    }

    @Test
    void legal_running이면_legal_비활성() throws Exception {
        Cookie cookie = loginCookie(createUser("admin"));
        int projectId = createProject(cookie);
        int sid = insertStatement(projectId);
        insertLog(projectId, sid, "classi", "success", "success");
        insertLog(projectId, sid, "safety-doc", "success", "success");
        insertRunningLog(projectId, sid, "legal", "running");

        mockMvc.perform(get("/projects/{pid}/agents/button-states", projectId)
                        .cookie(cookie)
                        .param("usageStatementId", String.valueOf(sid)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.legal.enabled").value(false))
                .andExpect(jsonPath("$.data.legal.reason").value("현재 실행 중입니다."));
    }

    // ─── report 버튼 ──────────────────────────────────────────────────────
    // 선행 조건: legal.status=success AND legal.result_code IN (success, hil)
    // 비활성 조건: report running/pending

    @Test
    void legal_없으면_report_비활성() throws Exception {
        Cookie cookie = loginCookie(createUser("admin"));
        int projectId = createProject(cookie);
        int sid = insertStatement(projectId);
        insertLog(projectId, sid, "classi", "success", "success");
        insertLog(projectId, sid, "safety-doc", "success", "success");

        mockMvc.perform(get("/projects/{pid}/agents/button-states", projectId)
                        .cookie(cookie)
                        .param("usageStatementId", String.valueOf(sid)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.report.enabled").value(false))
                .andExpect(jsonPath("$.data.report.reason").value("legal을 먼저 실행해야 합니다."));
    }

    @Test
    void legal_success_success이면_report_활성() throws Exception {
        Cookie cookie = loginCookie(createUser("admin"));
        int projectId = createProject(cookie);
        int sid = insertStatement(projectId);
        insertLog(projectId, sid, "classi", "success", "success");
        insertLog(projectId, sid, "safety-doc", "success", "success");
        insertLog(projectId, sid, "legal", "success", "success");

        mockMvc.perform(get("/projects/{pid}/agents/button-states", projectId)
                        .cookie(cookie)
                        .param("usageStatementId", String.valueOf(sid)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.report.enabled").value(true))
                .andExpect(jsonPath("$.data.report.reason").isEmpty());
    }

    @Test
    void legal_success_hil이면_report_활성() throws Exception {
        Cookie cookie = loginCookie(createUser("admin"));
        int projectId = createProject(cookie);
        int sid = insertStatement(projectId);
        insertLog(projectId, sid, "classi", "success", "success");
        insertLog(projectId, sid, "safety-doc", "success", "success");
        insertLog(projectId, sid, "legal", "success", "hil");

        mockMvc.perform(get("/projects/{pid}/agents/button-states", projectId)
                        .cookie(cookie)
                        .param("usageStatementId", String.valueOf(sid)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.report.enabled").value(true));
    }

    @Test
    void legal_success_fail이면_report_비활성() throws Exception {
        Cookie cookie = loginCookie(createUser("admin"));
        int projectId = createProject(cookie);
        int sid = insertStatement(projectId);
        insertLog(projectId, sid, "classi", "success", "success");
        insertLog(projectId, sid, "safety-doc", "success", "success");
        insertLog(projectId, sid, "legal", "success", "fail");

        mockMvc.perform(get("/projects/{pid}/agents/button-states", projectId)
                        .cookie(cookie)
                        .param("usageStatementId", String.valueOf(sid)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.report.enabled").value(false))
                .andExpect(jsonPath("$.data.report.reason").value("legal을 먼저 실행해야 합니다."));
    }

    @Test
    void report_running이면_report_비활성() throws Exception {
        Cookie cookie = loginCookie(createUser("admin"));
        int projectId = createProject(cookie);
        int sid = insertStatement(projectId);
        insertLog(projectId, sid, "classi", "success", "success");
        insertLog(projectId, sid, "safety-doc", "success", "success");
        insertLog(projectId, sid, "legal", "success", "success");
        insertRunningLog(projectId, sid, "report", "running");

        mockMvc.perform(get("/projects/{pid}/agents/button-states", projectId)
                        .cookie(cookie)
                        .param("usageStatementId", String.valueOf(sid)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.report.enabled").value(false))
                .andExpect(jsonPath("$.data.report.reason").value("현재 실행 중입니다."));
    }

    // ─── 전체 흐름 ────────────────────────────────────────────────────────

    @Test
    void 모든_선행_조건_충족_시_세_버튼_모두_활성() throws Exception {
        Cookie cookie = loginCookie(createUser("admin"));
        int projectId = createProject(cookie);
        int sid = insertStatement(projectId);
        insertLog(projectId, sid, "classi", "success", "success");
        insertLog(projectId, sid, "safety-doc", "success", "success");
        insertLog(projectId, sid, "link", "success", "hil");
        insertLog(projectId, sid, "vision", "success", "success");
        insertLog(projectId, sid, "legal", "success", "hil");

        mockMvc.perform(get("/projects/{pid}/agents/button-states", projectId)
                        .cookie(cookie)
                        .param("usageStatementId", String.valueOf(sid)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.validate.enabled").value(true))
                .andExpect(jsonPath("$.data.legal.enabled").value(true))
                .andExpect(jsonPath("$.data.report.enabled").value(true));
    }

    // ─── 공통: 인증·권한 ──────────────────────────────────────────────────

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
        int sid = insertStatement(projectId);

        mockMvc.perform(get("/projects/{pid}/agents/button-states", projectId)
                        .param("usageStatementId", String.valueOf(sid)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 비담당자_user는_403을_반환한다() throws Exception {
        Cookie adminCookie = loginCookie(createUser("admin"));
        Cookie outsiderCookie = loginCookie(createUser("user"));
        int projectId = createProject(adminCookie);
        int sid = insertStatement(projectId);

        mockMvc.perform(get("/projects/{pid}/agents/button-states", projectId)
                        .cookie(outsiderCookie)
                        .param("usageStatementId", String.valueOf(sid)))
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
        int sid = insertStatement(projectId);

        mockMvc.perform(get("/projects/{pid}/agents/button-states", projectId)
                        .cookie(userCookie)
                        .param("usageStatementId", String.valueOf(sid)))
                .andExpect(status().isOk());
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

    /** status=success/fail 인 완료 로그 — result_code 필수 */
    private void insertLog(int projectId, int statementId, String agentTypeCode, String statusCode, String resultCode) {
        jdbcTemplate.update("""
                INSERT INTO service.agent_logs
                    (project_id, usage_statement_id, agent_type_code, status_code, result_code)
                VALUES (?, ?, ?, ?, ?)
                """, projectId, statementId, agentTypeCode, statusCode, resultCode);
    }

    /** status=running/pending 인 실행 중 로그 — result_code NULL */
    private void insertRunningLog(int projectId, int statementId, String agentTypeCode, String statusCode) {
        jdbcTemplate.update("""
                INSERT INTO service.agent_logs
                    (project_id, usage_statement_id, agent_type_code, status_code)
                VALUES (?, ?, ?, ?)
                """, projectId, statementId, agentTypeCode, statusCode);
    }
}
