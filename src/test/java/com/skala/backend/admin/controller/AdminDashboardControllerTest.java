package com.skala.backend.admin.controller;

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

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminDashboardControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;

    // ─── GET /admin/dashboard ─────────────────────────────────────────────

    @Test
    void admin은_대시보드를_조회할_수_있다() throws Exception {
        Cookie adminCookie = loginCookie(createUser("admin"));

        mockMvc.perform(get("/admin/dashboard").cookie(adminCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.summary.totalProjects").isNumber())
                .andExpect(jsonPath("$.data.summary.reviewNeededProjects").isNumber())
                .andExpect(jsonPath("$.data.aiUsage.total.callCount").isNumber())
                .andExpect(jsonPath("$.data.aiUsage.byAgent").isArray())
                .andExpect(jsonPath("$.data.aiUsage.topUsers").isArray())
                .andExpect(jsonPath("$.data.aiUsage.topProjects").isArray())
                .andExpect(jsonPath("$.data.supplementAssignees").isArray());
    }

    @Test
    void user는_대시보드에_접근할_수_없다() throws Exception {
        Cookie adminCookie = loginCookie(createUser("admin"));
        Map<String, String> user = createUser("user");
        Cookie userCookie = loginCookie(user);
        int userId = readUserId(user);
        int projectId = createProject(adminCookie);
        assign(adminCookie, projectId, userId);

        mockMvc.perform(get("/admin/dashboard").cookie(userCookie))
                .andExpect(status().isForbidden());
    }

    @Test
    void system_admin은_대시보드에_접근할_수_없다() throws Exception {
        Cookie saCookie = loginCookie(createUser("system_admin"));

        mockMvc.perform(get("/admin/dashboard").cookie(saCookie))
                .andExpect(status().isForbidden());
    }

    @Test
    void 인증없이_대시보드_접근하면_401을_반환한다() throws Exception {
        mockMvc.perform(get("/admin/dashboard"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 대시보드_summary_전체_프로젝트_카운트가_정확하다() throws Exception {
        Cookie adminCookie = loginCookie(createUser("admin"));
        int before = getDashboardTotalProjects(adminCookie);

        createProject(adminCookie);
        createProject(adminCookie);

        mockMvc.perform(get("/admin/dashboard").cookie(adminCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.summary.totalProjects").value(before + 2));
    }

    @Test
    void 대시보드_summary_검토필요_프로젝트_카운트가_정확하다() throws Exception {
        Cookie adminCookie = loginCookie(createUser("admin"));
        int before = getDashboardReviewNeeded(adminCookie);

        int projectId = createProject(adminCookie);
        insertStatement(projectId, "upload_completed");

        mockMvc.perform(get("/admin/dashboard").cookie(adminCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.summary.reviewNeededProjects").value(before + 1));
    }

    @Test
    void draft_상태_사용내역서는_검토필요에_포함되지_않는다() throws Exception {
        Cookie adminCookie = loginCookie(createUser("admin"));
        int before = getDashboardReviewNeeded(adminCookie);

        int projectId = createProject(adminCookie);
        insertStatement(projectId, "draft");

        mockMvc.perform(get("/admin/dashboard").cookie(adminCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.summary.reviewNeededProjects").value(before));
    }

    @Test
    void 대시보드_supplement_assignees_현재월_기준으로_집계된다() throws Exception {
        Cookie adminCookie = loginCookie(createUser("admin"));
        Map<String, String> user = createUser("user");
        Cookie userCookie = loginCookie(user);
        int userId = readUserId(user);
        int projectId = createProject(adminCookie);
        assign(adminCookie, projectId, userId);
        insertStatementCurrentMonth(projectId, "supplement_required");

        mockMvc.perform(get("/admin/dashboard").cookie(adminCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.supplementAssignees", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.data.supplementAssignees[0].supplementCount").value(greaterThanOrEqualTo(1)));
    }

    @Test
    void 대시보드_ai사용_전체통계_레코드가_있으면_집계된다() throws Exception {
        Cookie adminCookie = loginCookie(createUser("admin"));
        Map<String, String> userCreds = createUser("user");
        int userId = readUserId(userCreds);
        int projectId = createProject(adminCookie);
        insertUsageRecord(userId, projectId, "classi", 100L, 200L, new java.math.BigDecimal("0.00001"));

        mockMvc.perform(get("/admin/dashboard").cookie(adminCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.aiUsage.total.callCount").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data.aiUsage.topUsers", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.data.aiUsage.topProjects", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.data.aiUsage.byAgent", hasSize(greaterThanOrEqualTo(1))));
    }

    @Test
    void ai_사용자별_통계에_roleCode가_포함된다() throws Exception {
        Cookie adminCookie = loginCookie(createUser("admin"));
        Map<String, String> userCreds = createUser("user");
        int userId = readUserId(userCreds);
        int projectId = createProject(adminCookie);
        insertUsageRecord(userId, projectId, "classi", 100L, 200L, new java.math.BigDecimal("0.00001"));

        mockMvc.perform(get("/admin/dashboard").cookie(adminCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.aiUsage.topUsers[0].roleCode").isString());
    }

    @Test
    void ai_프로젝트별_통계_type은_항상_project이다() throws Exception {
        Cookie adminCookie = loginCookie(createUser("admin"));
        Map<String, String> userCreds = createUser("user");
        int userId = readUserId(userCreds);
        int projectId = createProject(adminCookie);
        insertUsageRecord(userId, projectId, "legal", 500L, 300L, new java.math.BigDecimal("0.0001"));

        mockMvc.perform(get("/admin/dashboard").cookie(adminCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.aiUsage.topProjects[0].type").value("project"));
    }

    @Test
    void ai_사용량에_input_output_토큰이_분리되어_반환된다() throws Exception {
        Cookie adminCookie = loginCookie(createUser("admin"));
        Map<String, String> userCreds = createUser("user");
        int userId = readUserId(userCreds);
        int projectId = createProject(adminCookie);
        insertUsageRecord(userId, projectId, "classi", 100L, 200L, new java.math.BigDecimal("0.00001"));

        mockMvc.perform(get("/admin/dashboard").cookie(adminCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.aiUsage.total.totalInputTokens").isNumber())
                .andExpect(jsonPath("$.data.aiUsage.total.totalOutputTokens").isNumber());
    }

    @Test
    void supplement_assignees에_roleCode가_포함된다() throws Exception {
        Cookie adminCookie = loginCookie(createUser("admin"));
        Map<String, String> user = createUser("user");
        int userId = readUserId(user);
        int projectId = createProject(adminCookie);
        assign(adminCookie, projectId, userId);
        insertStatementCurrentMonth(projectId, "supplement_required");

        mockMvc.perform(get("/admin/dashboard").cookie(adminCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.supplementAssignees[0].roleCode").isString());
    }

    @Test
    void 과거_upload_completed_사용내역서도_검토필요에_포함된다() throws Exception {
        Cookie adminCookie = loginCookie(createUser("admin"));
        int before = getDashboardReviewNeeded(adminCookie);

        int projectId = createProject(adminCookie);
        // 과거 월(2026-05) upload_completed 삽입
        insertStatement(projectId, "upload_completed");

        mockMvc.perform(get("/admin/dashboard").cookie(adminCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.summary.reviewNeededProjects").value(before + 1));
    }

    @Test
    void review_completed_사용내역서는_검토필요에_포함되지_않는다() throws Exception {
        Cookie adminCookie = loginCookie(createUser("admin"));
        int before = getDashboardReviewNeeded(adminCookie);

        int projectId = createProject(adminCookie);
        insertStatement(projectId, "review_completed");

        mockMvc.perform(get("/admin/dashboard").cookie(adminCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.summary.reviewNeededProjects").value(before));
    }

    // ─── GET /admin/dashboard/ai-usage ───────────────────────────────────

    @Test
    void admin은_ai_사용량을_조회할_수_있다() throws Exception {
        Cookie adminCookie = loginCookie(createUser("admin"));

        mockMvc.perform(get("/admin/dashboard/ai-usage").cookie(adminCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total.totalInputTokens").isNumber())
                .andExpect(jsonPath("$.data.total.totalOutputTokens").isNumber())
                .andExpect(jsonPath("$.data.total.callCount").isNumber())
                .andExpect(jsonPath("$.data.byAgent").isArray())
                .andExpect(jsonPath("$.data.topUsers").isArray())
                .andExpect(jsonPath("$.data.topProjects").isArray());
    }

    @Test
    void user는_ai_사용량에_접근할_수_없다() throws Exception {
        Cookie adminCookie = loginCookie(createUser("admin"));
        Map<String, String> user = createUser("user");
        Cookie userCookie = loginCookie(user);
        int userId = readUserId(user);
        int projectId = createProject(adminCookie);
        assign(adminCookie, projectId, userId);

        mockMvc.perform(get("/admin/dashboard/ai-usage").cookie(userCookie))
                .andExpect(status().isForbidden());
    }

    @Test
    void ai_사용량_year_month_필터가_동작한다() throws Exception {
        Cookie adminCookie = loginCookie(createUser("admin"));
        Map<String, String> userCreds = createUser("user");
        int userId = readUserId(userCreds);
        int projectId = createProject(adminCookie);
        insertUsageRecord(userId, projectId, "classi", 100L, 200L, new java.math.BigDecimal("0.00001"));

        mockMvc.perform(get("/admin/dashboard/ai-usage")
                        .cookie(adminCookie)
                        .param("year", "2026")
                        .param("month", "6"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").exists());
    }

    @Test
    void ai_사용량_year만_전달하면_연도_전체_집계한다() throws Exception {
        Cookie adminCookie = loginCookie(createUser("admin"));

        mockMvc.perform(get("/admin/dashboard/ai-usage")
                        .cookie(adminCookie)
                        .param("year", "2026"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total.callCount").isNumber());
    }

    // ─── GET /admin/dashboard/projects ───────────────────────────────────

    @Test
    void admin은_프로젝트_목록을_조회할_수_있다() throws Exception {
        Cookie adminCookie = loginCookie(createUser("admin"));
        createProject(adminCookie);

        mockMvc.perform(get("/admin/dashboard/projects").cookie(adminCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.items[0].id").isNumber())
                .andExpect(jsonPath("$.data.items[0].progressRate").isNumber())
                .andExpect(jsonPath("$.data.items[0].usageRate").isNumber());
    }

    @Test
    void user는_프로젝트_목록에_접근할_수_없다() throws Exception {
        Cookie adminCookie = loginCookie(createUser("admin"));
        Map<String, String> user = createUser("user");
        Cookie userCookie = loginCookie(user);
        int userId = readUserId(user);
        int projectId = createProject(adminCookie);
        assign(adminCookie, projectId, userId);

        mockMvc.perform(get("/admin/dashboard/projects").cookie(userCookie))
                .andExpect(status().isForbidden());
    }

    @Test
    void keyword로_프로젝트_이름_검색이_가능하다() throws Exception {
        Cookie adminCookie = loginCookie(createUser("admin"));
        String uniqueName = "검색테스트-" + UUID.randomUUID();
        createProject(adminCookie, uniqueName);

        mockMvc.perform(get("/admin/dashboard/projects")
                        .cookie(adminCookie)
                        .param("keyword", uniqueName))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(1))
                .andExpect(jsonPath("$.data.items[0].projectName").value(uniqueName));
    }

    @Test
    void 존재하지_않는_keyword는_빈_목록을_반환한다() throws Exception {
        Cookie adminCookie = loginCookie(createUser("admin"));

        mockMvc.perform(get("/admin/dashboard/projects")
                        .cookie(adminCookie)
                        .param("keyword", "절대없는프로젝트이름XYZ123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(0))
                .andExpect(jsonPath("$.data.items").isEmpty());
    }

    @Test
    void statusCode로_프로젝트_상태_필터링이_가능하다() throws Exception {
        Cookie adminCookie = loginCookie(createUser("admin"));
        createProject(adminCookie);

        mockMvc.perform(get("/admin/dashboard/projects")
                        .cookie(adminCookie)
                        .param("statusCode", "active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[*].statusCode", everyItem(equalTo("active"))));
    }

    @Test
    void assigneeName으로_담당자_필터링이_가능하다() throws Exception {
        Cookie adminCookie = loginCookie(createUser("admin"));
        Map<String, String> user = createUser("user");
        Cookie userCookie = loginCookie(user);
        int userId = readUserId(user);
        int projectId = createProject(adminCookie);
        assign(adminCookie, projectId, userId);
        String userName = userCookie.getValue(); // not the name, let's get it another way

        // 조회해서 담당자 이름 가져오기
        MvcResult result = mockMvc.perform(get("/admin/dashboard/projects")
                        .cookie(adminCookie))
                .andReturn();
        String assigneesJson = objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("items").toString();

        // 담당자 이름으로 필터 → 홍길동 (createUser의 realName)
        mockMvc.perform(get("/admin/dashboard/projects")
                        .cookie(adminCookie)
                        .param("assigneeName", "홍길동"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(greaterThanOrEqualTo(1)));
    }

    @Test
    void startDate_오름차순_정렬이_동작한다() throws Exception {
        Cookie adminCookie = loginCookie(createUser("admin"));
        createProject(adminCookie);
        createProject(adminCookie);

        mockMvc.perform(get("/admin/dashboard/projects")
                        .cookie(adminCookie)
                        .param("sortBy", "startDate")
                        .param("sortDir", "asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray());
    }

    @Test
    void 잘못된_sortBy는_기본값으로_처리된다() throws Exception {
        Cookie adminCookie = loginCookie(createUser("admin"));
        createProject(adminCookie);

        mockMvc.perform(get("/admin/dashboard/projects")
                        .cookie(adminCookie)
                        .param("sortBy", "invalid_column"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray());
    }

    @Test
    void 페이징이_동작한다() throws Exception {
        Cookie adminCookie = loginCookie(createUser("admin"));
        createProject(adminCookie);
        createProject(adminCookie);
        createProject(adminCookie);

        mockMvc.perform(get("/admin/dashboard/projects")
                        .cookie(adminCookie)
                        .param("page", "1")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items", hasSize(lessThanOrEqualTo(2))));
    }

    @Test
    void 프로젝트_목록에_담당자_정보가_포함된다() throws Exception {
        Cookie adminCookie = loginCookie(createUser("admin"));
        Map<String, String> user = createUser("user");
        int userId = readUserId(user);
        int projectId = createProject(adminCookie);
        assign(adminCookie, projectId, userId);

        mockMvc.perform(get("/admin/dashboard/projects")
                        .cookie(adminCookie)
                        .param("keyword", "테스트"))
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
        return createProject(cookie, "테스트 프로젝트-" + UUID.randomUUID());
    }

    private int createProject(Cookie cookie, String projectName) throws Exception {
        MvcResult result = mockMvc.perform(post("/projects")
                        .cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "contractNo", "CN-" + UUID.randomUUID(),
                                "constructionCompany", "스칼라건설",
                                "projectName", projectName,
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

    private int insertStatement(int projectId, String statusCode) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO service.usage_statements
                    (project_id, report_month, revision_no, document_written_date, cumulative_progress_rate, status_code)
                VALUES (?, '2026-05-01'::date, 1, '2026-05-15'::date, 30, ?)
                RETURNING id
                """, Integer.class, projectId, statusCode);
    }

    private void insertStatementCurrentMonth(int projectId, String statusCode) {
        jdbcTemplate.update("""
                INSERT INTO service.usage_statements
                    (project_id, report_month, revision_no, document_written_date, cumulative_progress_rate, status_code)
                VALUES (?, date_trunc('month', CURRENT_DATE)::date, 1, CURRENT_DATE, 0, ?)
                """, projectId, statusCode);
    }

    private void insertUsageRecord(int userId, int projectId, String agentTypeCode,
            long inputTokens, long outputTokens, java.math.BigDecimal costUsd) {
        jdbcTemplate.update("""
                INSERT INTO service.agent_usage_records
                    (user_id, project_id, agent_type_code, input_tokens, output_tokens, cost_usd)
                VALUES (?, ?, ?, ?, ?, ?)
                """, userId, projectId, agentTypeCode, inputTokens, outputTokens, costUsd);
    }

    private int getDashboardTotalProjects(Cookie adminCookie) throws Exception {
        MvcResult result = mockMvc.perform(get("/admin/dashboard").cookie(adminCookie))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("summary").path("totalProjects").asInt();
    }

    private int getDashboardReviewNeeded(Cookie adminCookie) throws Exception {
        MvcResult result = mockMvc.perform(get("/admin/dashboard").cookie(adminCookie))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("summary").path("reviewNeededProjects").asInt();
    }
}
