package com.skala.backend.evidence.controller;

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

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class EvidenceRequirementControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;

    // ─── 서류 충족 현황 조회 (/evidence-requirements) ─────────────────────

    @Test
    void 활성_요건의_satisfied_여부를_모두_반환한다() throws Exception {
        Cookie adminCookie = loginCookie(createUser("admin"));
        int projectId = createProject(adminCookie);
        int statementId = insertStatement(projectId);
        int itemId = insertItem(statementId);

        insertRequirement(itemId, "receipt", true, true);
        insertRequirement(itemId, "pay_stub", false, true);

        mockMvc.perform(get("/projects/{pid}/usage-statement-items/{iid}/evidence-requirements", projectId, itemId)
                        .cookie(adminCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[?(@.evidenceTypeCode == 'receipt')].satisfied").value(true))
                .andExpect(jsonPath("$.data[?(@.evidenceTypeCode == 'pay_stub')].satisfied").value(false));
    }

    @Test
    void 비활성_요건은_결과에서_제외된다() throws Exception {
        Cookie adminCookie = loginCookie(createUser("admin"));
        int projectId = createProject(adminCookie);
        int statementId = insertStatement(projectId);
        int itemId = insertItem(statementId);

        insertRequirement(itemId, "receipt", true, true);
        insertRequirement(itemId, "pay_stub", false, false); // is_active = false

        mockMvc.perform(get("/projects/{pid}/usage-statement-items/{iid}/evidence-requirements", projectId, itemId)
                        .cookie(adminCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].evidenceTypeCode").value("receipt"));
    }

    @Test
    void 요건이_없는_항목은_빈_배열을_반환한다() throws Exception {
        Cookie adminCookie = loginCookie(createUser("admin"));
        int projectId = createProject(adminCookie);
        int statementId = insertStatement(projectId);
        int itemId = insertItem(statementId);

        mockMvc.perform(get("/projects/{pid}/usage-statement-items/{iid}/evidence-requirements", projectId, itemId)
                        .cookie(adminCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test
    void 존재하지_않는_항목_조회_시_404를_반환한다() throws Exception {
        Cookie adminCookie = loginCookie(createUser("admin"));
        int projectId = createProject(adminCookie);

        mockMvc.perform(get("/projects/{pid}/usage-statement-items/{iid}/evidence-requirements", projectId, 999999)
                        .cookie(adminCookie))
                .andExpect(status().isNotFound());
    }

    @Test
    void 다른_프로젝트_항목_조회_시_404를_반환한다() throws Exception {
        Cookie adminCookie = loginCookie(createUser("admin"));
        int projectId = createProject(adminCookie);
        int otherProjectId = createProject(adminCookie);
        int otherStatementId = insertStatement(otherProjectId);
        int otherItemId = insertItem(otherStatementId);

        mockMvc.perform(get("/projects/{pid}/usage-statement-items/{iid}/evidence-requirements", projectId, otherItemId)
                        .cookie(adminCookie))
                .andExpect(status().isNotFound());
    }

    @Test
    void 쿠키_없이_조회하면_401을_반환한다() throws Exception {
        Cookie adminCookie = loginCookie(createUser("admin"));
        int projectId = createProject(adminCookie);
        int statementId = insertStatement(projectId);
        int itemId = insertItem(statementId);

        mockMvc.perform(get("/projects/{pid}/usage-statement-items/{iid}/evidence-requirements", projectId, itemId))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 담당자가_아닌_user는_조회할_수_없다() throws Exception {
        Cookie adminCookie = loginCookie(createUser("admin"));
        Cookie outsiderCookie = loginCookie(createUser("user"));
        int projectId = createProject(adminCookie);
        int statementId = insertStatement(projectId);
        int itemId = insertItem(statementId);

        mockMvc.perform(get("/projects/{pid}/usage-statement-items/{iid}/evidence-requirements", projectId, itemId)
                        .cookie(outsiderCookie))
                .andExpect(status().isForbidden());
    }

    @Test
    void 담당자_user는_조회할_수_있다() throws Exception {
        Cookie adminCookie = loginCookie(createUser("admin"));
        Map<String, String> user = createUser("user");
        Cookie userCookie = loginCookie(user);
        int userId = readUserId(user);
        int projectId = createProject(adminCookie);
        assign(adminCookie, projectId, userId);
        int statementId = insertStatement(projectId);
        int itemId = insertItem(statementId);
        insertRequirement(itemId, "receipt", false, true);

        mockMvc.perform(get("/projects/{pid}/usage-statement-items/{iid}/evidence-requirements", projectId, itemId)
                        .cookie(userCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)));
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

    private int insertStatement(int projectId) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO service.usage_statements
                    (project_id, report_month, revision_no, document_written_date, cumulative_progress_rate)
                VALUES (?, '2026-05-01', 1, '2026-05-01', 30)
                RETURNING id
                """, Integer.class, projectId);
    }

    private int insertItem(int statementId) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO service.usage_statement_items
                    (usage_statement_id, category_code, used_on, item_name, unit, quantity, unit_price, total_amount, page_no)
                VALUES (?, 'CAT_01', '2026-05-01', '안전관리자 임금', '월', 1, 500000, 500000, 1)
                RETURNING id
                """, Integer.class, statementId);
    }

    private void insertRequirement(int itemId, String evidenceTypeCode, boolean isSatisfied, boolean isActive) {
        jdbcTemplate.update("""
                INSERT INTO service.evidence_requirements
                    (usage_statement_item_id, evidence_type_code, is_satisfied, is_active)
                VALUES (?, ?, ?, ?)
                """, itemId, evidenceTypeCode, isSatisfied, isActive);
    }
}
