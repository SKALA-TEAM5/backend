package com.skala.backend.dashboard.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skala.backend.user.domain.RoleCode;
import com.skala.backend.user.domain.User;
import com.skala.backend.user.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class DashboardControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        // agent_logs가 usage_statement_items를 FK 참조하므로 agent_logs를 먼저 삭제
        jdbcTemplate.update("DELETE FROM service.agent_usage_records");
        jdbcTemplate.update("DELETE FROM service.evidence_requirements");
        jdbcTemplate.update("DELETE FROM service.evidence_file_links");
        jdbcTemplate.update("DELETE FROM service.agent_logs");
        jdbcTemplate.update("DELETE FROM service.usage_statement_items");
        jdbcTemplate.update("DELETE FROM service.usage_statement_summaries");
        jdbcTemplate.update("DELETE FROM service.usage_statements");
        jdbcTemplate.update("DELETE FROM service.project_user_assignments");
        jdbcTemplate.update("DELETE FROM service.files");
        jdbcTemplate.update("DELETE FROM service.projects");
    }

    // ─── GET /dashboard/summary ───────────────────────────────────────────────

    @Test
    void admin은_전체_및_검토필요_프로젝트_수를_반환한다() throws Exception {
        Cookie adminCookie = loginCookie(createUser("admin"));
        int p1 = createProject(adminCookie);
        int p2 = createProject(adminCookie);
        int p3 = createProject(adminCookie);
        insertStatement(p1, "upload_completed",    thisMonth());
        insertStatement(p2, "upload_completed",    thisMonth());
        insertStatement(p3, "supplement_required", thisMonth());

        mockMvc.perform(get("/dashboard/summary").cookie(adminCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalProjects").value(3))
                .andExpect(jsonPath("$.data.reviewRequiredCount").value(2));
    }

    @Test
    void 프로젝트가_없으면_0_0을_반환한다() throws Exception {
        Cookie adminCookie = loginCookie(createUser("admin"));

        mockMvc.perform(get("/dashboard/summary").cookie(adminCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalProjects").value(0))
                .andExpect(jsonPath("$.data.reviewRequiredCount").value(0));
    }

    @Test
    void upload_completed가_없으면_검토필요_카운트_0을_반환한다() throws Exception {
        Cookie adminCookie = loginCookie(createUser("admin"));
        int p1 = createProject(adminCookie);
        insertStatement(p1, "supplement_required", thisMonth());

        mockMvc.perform(get("/dashboard/summary").cookie(adminCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalProjects").value(1))
                .andExpect(jsonPath("$.data.reviewRequiredCount").value(0));
    }

    @Test
    void upload_completed_상태만_검토필요로_집계된다() throws Exception {
        Cookie adminCookie = loginCookie(createUser("admin"));
        int p1 = createProject(adminCookie);
        insertStatement(p1, "upload_completed", thisMonth());

        mockMvc.perform(get("/dashboard/summary").cookie(adminCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalProjects").value(1))
                .andExpect(jsonPath("$.data.reviewRequiredCount").value(1));
    }

    @Test
    void user는_summary_조회시_403을_반환한다() throws Exception {
        Cookie userCookie = loginCookie(createUser("user"));
        mockMvc.perform(get("/dashboard/summary").cookie(userCookie))
                .andExpect(status().isForbidden());
    }

    @Test
    void 미인증_summary_조회는_401을_반환한다() throws Exception {
        mockMvc.perform(get("/dashboard/summary"))
                .andExpect(status().isUnauthorized());
    }

    // ─── GET /dashboard/ai-usage ──────────────────────────────────────────────

    @Test
    void 전체_토큰과_호출수_합계를_반환한다() throws Exception {
        Cookie adminCookie = loginCookie(createUser("admin"));
        Map<String, String> userCreds = createUser("user");
        int userId = readUserId(userCreds);
        int projectId = createProject(adminCookie);

        insertUsageRecord(userId, projectId, "legal",      100, 50, "0.01000000");
        insertUsageRecord(userId, projectId, "safety-doc", 200, 80, "0.02000000");

        mockMvc.perform(get("/dashboard/ai-usage").cookie(adminCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total.totalTokens").value(430))
                .andExpect(jsonPath("$.data.total.totalCalls").value(2));
    }

    @Test
    void year_month_필터로_특정_월만_집계된다() throws Exception {
        Cookie adminCookie = loginCookie(createUser("admin"));
        int userId = readUserId(createUser("user"));
        int projectId = createProject(adminCookie);

        insertUsageRecordAt(userId, projectId, "legal", 100, 50, "0.01000000", "2026-04-15T00:00:00Z");
        insertUsageRecordAt(userId, projectId, "legal", 200, 80, "0.02000000", "2026-05-15T00:00:00Z");

        mockMvc.perform(get("/dashboard/ai-usage").cookie(adminCookie)
                        .param("year", "2026")
                        .param("month", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total.totalTokens").value(280))
                .andExpect(jsonPath("$.data.total.totalCalls").value(1));
    }

    @Test
    void year만_전달하면_해당_연도_전체를_집계한다() throws Exception {
        Cookie adminCookie = loginCookie(createUser("admin"));
        int userId = readUserId(createUser("user"));
        int projectId = createProject(adminCookie);

        insertUsageRecordAt(userId, projectId, "legal", 100, 50, "0.01000000", "2026-01-10T00:00:00Z");
        insertUsageRecordAt(userId, projectId, "legal", 200, 80, "0.02000000", "2026-06-10T00:00:00Z");
        insertUsageRecordAt(userId, projectId, "legal", 300, 100, "0.03000000", "2025-12-10T00:00:00Z"); // 제외

        mockMvc.perform(get("/dashboard/ai-usage").cookie(adminCookie)
                        .param("year", "2026"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total.totalTokens").value(430))
                .andExpect(jsonPath("$.data.total.totalCalls").value(2));
    }

    @Test
    void 데이터_없으면_0으로_반환된다() throws Exception {
        Cookie adminCookie = loginCookie(createUser("admin"));

        mockMvc.perform(get("/dashboard/ai-usage").cookie(adminCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total.totalTokens").value(0))
                .andExpect(jsonPath("$.data.total.totalCalls").value(0))
                .andExpect(jsonPath("$.data.byUser").isArray())
                .andExpect(jsonPath("$.data.byProject").isArray());
    }

    @Test
    void byUser는_비용_내림차순_TOP5만_반환한다() throws Exception {
        Cookie adminCookie = loginCookie(createUser("admin"));
        int projectId = createProject(adminCookie);
        for (int i = 1; i <= 6; i++) {
            int uid = readUserId(createUser("user"));
            insertUsageRecord(uid, projectId, "legal", 100 * i, 50, "0.0" + i + "000000");
        }

        mockMvc.perform(get("/dashboard/ai-usage").cookie(adminCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.byUser.length()").value(5));
    }

    @Test
    void byProject는_비용_내림차순_TOP5만_반환한다() throws Exception {
        Cookie adminCookie = loginCookie(createUser("admin"));
        int userId = readUserId(createUser("user"));
        for (int i = 1; i <= 6; i++) {
            int pid = createProject(adminCookie);
            insertUsageRecord(userId, pid, "legal", 100 * i, 50, "0.0" + i + "000000");
        }

        mockMvc.perform(get("/dashboard/ai-usage").cookie(adminCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.byProject.length()").value(5));
    }

    @Test
    void byUser_응답에_roleCode가_포함된다() throws Exception {
        Cookie adminCookie = loginCookie(createUser("admin"));
        int userId = readUserId(createUser("user"));
        int projectId = createProject(adminCookie);
        insertUsageRecord(userId, projectId, "legal", 100, 50, "0.01000000");

        mockMvc.perform(get("/dashboard/ai-usage").cookie(adminCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.byUser[0].roleCode").value("user"))
                .andExpect(jsonPath("$.data.byUser[0].userName").isString());
    }

    @Test
    void user는_ai_usage_조회시_403을_반환한다() throws Exception {
        Cookie userCookie = loginCookie(createUser("user"));
        mockMvc.perform(get("/dashboard/ai-usage").cookie(userCookie))
                .andExpect(status().isForbidden());
    }

    @Test
    void 미인증_ai_usage_조회는_401을_반환한다() throws Exception {
        mockMvc.perform(get("/dashboard/ai-usage"))
                .andExpect(status().isUnauthorized());
    }

    // ─── GET /dashboard/supplement-progress ───────────────────────────────────

    @Test
    void 이번달_보완요청_담당자별_집계를_반환한다() throws Exception {
        Cookie adminCookie = loginCookie(createUser("admin"));

        Map<String, String> u1Creds = createUser("user");
        int user1Id = readUserId(u1Creds);
        int p1 = createProject(adminCookie);
        int p2 = createProject(adminCookie);
        // admin은 프로젝트 생성 시 자동 배정되므로, 담당자를 user1만으로 교체
        setAssignees(adminCookie, p1, user1Id);
        setAssignees(adminCookie, p2, user1Id);
        insertStatement(p1, "supplement_required", thisMonth());
        insertStatement(p2, "supplement_required", thisMonth());

        Map<String, String> u2Creds = createUser("user");
        int user2Id = readUserId(u2Creds);
        int p3 = createProject(adminCookie);
        setAssignees(adminCookie, p3, user2Id);
        insertStatement(p3, "supplement_required", thisMonth());

        mockMvc.perform(get("/dashboard/supplement-progress").cookie(adminCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].supplementCount").value(2))
                .andExpect(jsonPath("$.data[1].supplementCount").value(1));
    }

    @Test
    void 다른달_보완요청은_포함되지_않는다() throws Exception {
        Cookie adminCookie = loginCookie(createUser("admin"));
        int userId = readUserId(createUser("user"));
        int projectId = createProject(adminCookie);
        assignUser(adminCookie, projectId, userId);
        insertStatement(projectId, "supplement_required", "2026-03-01"); // 과거 달

        mockMvc.perform(get("/dashboard/supplement-progress").cookie(adminCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void supplement_required가_아닌_상태는_포함되지_않는다() throws Exception {
        Cookie adminCookie = loginCookie(createUser("admin"));
        int userId = readUserId(createUser("user"));
        int projectId = createProject(adminCookie);
        assignUser(adminCookie, projectId, userId);
        insertStatement(projectId, "upload_completed", thisMonth());

        mockMvc.perform(get("/dashboard/supplement-progress").cookie(adminCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void 내림차순으로_최대_3명만_반환된다() throws Exception {
        Cookie adminCookie = loginCookie(createUser("admin"));
        // 4명: supplementCount = 4, 3, 2, 1
        for (int i = 0; i < 4; i++) {
            int uid = readUserId(createUser("user"));
            for (int j = 0; j <= i; j++) {
                int pid = createProject(adminCookie);
                // admin은 자동 배정되므로, 담당자를 uid만으로 교체
                setAssignees(adminCookie, pid, uid);
                insertStatement(pid, "supplement_required", thisMonth());
            }
        }

        mockMvc.perform(get("/dashboard/supplement-progress").cookie(adminCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[0].supplementCount").value(4))
                .andExpect(jsonPath("$.data[1].supplementCount").value(3))
                .andExpect(jsonPath("$.data[2].supplementCount").value(2));
    }

    @Test
    void user는_supplement_progress_조회시_403을_반환한다() throws Exception {
        Cookie userCookie = loginCookie(createUser("user"));
        mockMvc.perform(get("/dashboard/supplement-progress").cookie(userCookie))
                .andExpect(status().isForbidden());
    }

    @Test
    void 미인증_supplement_progress_조회는_401을_반환한다() throws Exception {
        mockMvc.perform(get("/dashboard/supplement-progress"))
                .andExpect(status().isUnauthorized());
    }

    // ─── fixtures ─────────────────────────────────────────────────────────────

    private String thisMonth() {
        return LocalDate.now().withDayOfMonth(1).toString();
    }

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
                                "password",   credentials.get("password")
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
                                "password",   credentials.get("password")
                        ))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("user").path("id").asInt();
    }

    private int createProject(Cookie cookie) throws Exception {
        return createProjectWithNameAndContractNo(cookie,
                "테스트 프로젝트-" + UUID.randomUUID(), "CN-" + UUID.randomUUID());
    }

    private int createProjectWithNameAndContractNo(Cookie cookie, String projectName, String contractNo) throws Exception {
        MvcResult result = mockMvc.perform(post("/projects")
                        .cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "contractNo",             contractNo,
                                "constructionCompany",    "스칼라건설",
                                "projectName",            projectName,
                                "siteLocation",           "서울시 강남구",
                                "contractAmount",         100_000_000,
                                "constructionStartDate",  "2026-01-01",
                                "constructionEndDate",    "2026-12-31",
                                "appropriatedAmount",     10_000_000
                        ))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("project").path("id").asInt();
    }

    private void assignUser(Cookie adminCookie, int projectId, int userId) throws Exception {
        mockMvc.perform(post("/projects/{pid}/assignees/{uid}", projectId, userId)
                        .cookie(adminCookie))
                .andExpect(status().isOk());
    }

    private void setAssignees(Cookie adminCookie, int projectId, int... userIds) throws Exception {
        java.util.List<Integer> ids = new java.util.ArrayList<>();
        for (int uid : userIds) ids.add(uid);
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/projects/{pid}/assignees", projectId)
                        .cookie(adminCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("assigneeUserIds", ids))))
                .andExpect(status().isOk());
    }

    private int insertStatement(int projectId, String statusCode, String reportMonth) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO service.usage_statements
                    (project_id, report_month, revision_no, document_written_date,
                     cumulative_progress_rate, status_code)
                VALUES (?, ?::date, 1, ?::date, 30, ?)
                RETURNING id
                """, Integer.class, projectId, reportMonth, reportMonth, statusCode);
    }

    private void insertUsageRecord(int userId, int projectId, String agentTypeCode,
            long inputTokens, long outputTokens, String costUsd) {
        jdbcTemplate.update("""
                INSERT INTO service.agent_usage_records
                    (user_id, project_id, agent_type_code, input_tokens, output_tokens, cost_usd)
                VALUES (?, ?, ?, ?, ?, ?)
                """, userId, projectId, agentTypeCode,
                inputTokens, outputTokens, new BigDecimal(costUsd));
    }

    private void insertUsageRecordAt(int userId, int projectId, String agentTypeCode,
            long inputTokens, long outputTokens, String costUsd, String createdAt) {
        jdbcTemplate.update("""
                INSERT INTO service.agent_usage_records
                    (user_id, project_id, agent_type_code, input_tokens, output_tokens, cost_usd, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?::timestamptz)
                """, userId, projectId, agentTypeCode,
                inputTokens, outputTokens, new BigDecimal(costUsd), createdAt);
    }
}
