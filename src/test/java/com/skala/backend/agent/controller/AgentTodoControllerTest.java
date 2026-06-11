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

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AgentTodoControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;

    // ─── GET /agents/todos ────────────────────────────────────────────────

    @Test
    void 로그_없으면_validate_빈배열_legal_null을_반환한다() throws Exception {
        Cookie cookie = loginCookie(createUser("admin"));
        int projectId = createProject(cookie);
        int statementId = insertStatement(projectId);

        mockMvc.perform(get("/projects/{pid}/agents/todos", projectId)
                        .cookie(cookie)
                        .param("usageStatementId", String.valueOf(statementId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.validate").isArray())
                .andExpect(jsonPath("$.data.validate", hasSize(0)))
                .andExpect(jsonPath("$.data.legal").isEmpty());
    }

    @Test
    void safety_doc_hil이면_validate에_포함된다() throws Exception {
        Cookie cookie = loginCookie(createUser("admin"));
        int projectId = createProject(cookie);
        int statementId = insertStatement(projectId);
        int itemId = insertItem(statementId);
        insertTodoLog(projectId, statementId, "safety-doc", "success", "hil",
                "필수 증빙 누락 항목 1건",
                "[{\"usage_statement_item_id\":" + itemId + ",\"reason\":\"필수 증빙 누락: 안전교육일지\"}]");

        mockMvc.perform(get("/projects/{pid}/agents/todos", projectId)
                        .cookie(cookie)
                        .param("usageStatementId", String.valueOf(statementId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.validate", hasSize(1)))
                .andExpect(jsonPath("$.data.validate[0].agentTypeCode").value("safety-doc"))
                .andExpect(jsonPath("$.data.validate[0].resultCode").value("hil"))
                .andExpect(jsonPath("$.data.validate[0].reason").value("필수 증빙 누락 항목 1건"))
                .andExpect(jsonPath("$.data.validate[0].items", hasSize(1)))
                .andExpect(jsonPath("$.data.validate[0].items[0].usageStatementItemId").value(itemId))
                .andExpect(jsonPath("$.data.validate[0].items[0].reason").value("필수 증빙 누락: 안전교육일지"));
    }

    @Test
    void link_fail이면_validate에_포함되고_items는_빈배열이다() throws Exception {
        Cookie cookie = loginCookie(createUser("admin"));
        int projectId = createProject(cookie);
        int statementId = insertStatement(projectId);
        insertTodoLog(projectId, statementId, "link", "fail", "fail",
                "link Agent 실행 실패", null);

        mockMvc.perform(get("/projects/{pid}/agents/todos", projectId)
                        .cookie(cookie)
                        .param("usageStatementId", String.valueOf(statementId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.validate", hasSize(1)))
                .andExpect(jsonPath("$.data.validate[0].agentTypeCode").value("link"))
                .andExpect(jsonPath("$.data.validate[0].resultCode").value("fail"))
                .andExpect(jsonPath("$.data.validate[0].items", hasSize(0)));
    }

    @Test
    void vision_hil이면_validate에_포함된다() throws Exception {
        Cookie cookie = loginCookie(createUser("admin"));
        int projectId = createProject(cookie);
        int statementId = insertStatement(projectId);
        insertTodoLog(projectId, statementId, "vision", "success", "hil",
                "현장사진 검토 보완 필요",
                "[{\"usage_statement_item_id\":null,\"reason\":\"현장 안전시설 미확인\"}]");

        mockMvc.perform(get("/projects/{pid}/agents/todos", projectId)
                        .cookie(cookie)
                        .param("usageStatementId", String.valueOf(statementId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.validate", hasSize(1)))
                .andExpect(jsonPath("$.data.validate[0].agentTypeCode").value("vision"))
                .andExpect(jsonPath("$.data.validate[0].items[0].usageStatementItemId").isEmpty())
                .andExpect(jsonPath("$.data.validate[0].items[0].reason").value("현장 안전시설 미확인"));
    }

    @Test
    void validate_3개_모두_hil이면_전부_반환된다() throws Exception {
        Cookie cookie = loginCookie(createUser("admin"));
        int projectId = createProject(cookie);
        int statementId = insertStatement(projectId);
        insertTodoLog(projectId, statementId, "safety-doc", "success", "hil", "누락 1건", null);
        insertTodoLog(projectId, statementId, "link",       "success", "hil", "매칭 검토 1건", null);
        insertTodoLog(projectId, statementId, "vision",     "success", "hil", "현장사진 보완", null);

        mockMvc.perform(get("/projects/{pid}/agents/todos", projectId)
                        .cookie(cookie)
                        .param("usageStatementId", String.valueOf(statementId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.validate", hasSize(3)));
    }

    @Test
    void success_agent는_포함되지_않는다() throws Exception {
        Cookie cookie = loginCookie(createUser("admin"));
        int projectId = createProject(cookie);
        int statementId = insertStatement(projectId);
        insertTodoLog(projectId, statementId, "safety-doc", "success", "success", "누락 없음", null);
        insertTodoLog(projectId, statementId, "link",       "success", "hil",     "매칭 검토 1건", null);

        mockMvc.perform(get("/projects/{pid}/agents/todos", projectId)
                        .cookie(cookie)
                        .param("usageStatementId", String.valueOf(statementId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.validate", hasSize(1)))
                .andExpect(jsonPath("$.data.validate[0].agentTypeCode").value("link"));
    }

    @Test
    void legal_hil이면_legal_필드에_반환된다() throws Exception {
        Cookie cookie = loginCookie(createUser("admin"));
        int projectId = createProject(cookie);
        int statementId = insertStatement(projectId);
        int itemId = insertItem(statementId);
        insertTodoLog(projectId, statementId, "legal", "success", "hil",
                "법령 검토 결과 보고서 반영 대상 1건",
                "[{\"usage_statement_item_id\":" + itemId + ",\"reason\":\"법령 검토 필요: 한도 초과\"}]");

        mockMvc.perform(get("/projects/{pid}/agents/todos", projectId)
                        .cookie(cookie)
                        .param("usageStatementId", String.valueOf(statementId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.validate", hasSize(0)))
                .andExpect(jsonPath("$.data.legal.agentTypeCode").value("legal"))
                .andExpect(jsonPath("$.data.legal.resultCode").value("hil"))
                .andExpect(jsonPath("$.data.legal.items", hasSize(1)))
                .andExpect(jsonPath("$.data.legal.items[0].usageStatementItemId").value(itemId))
                .andExpect(jsonPath("$.data.legal.items[0].reason").value("법령 검토 필요: 한도 초과"));
    }

    @Test
    void legal_success이면_legal은_null이다() throws Exception {
        Cookie cookie = loginCookie(createUser("admin"));
        int projectId = createProject(cookie);
        int statementId = insertStatement(projectId);
        insertTodoLog(projectId, statementId, "legal", "success", "success", "특이사항 없음", null);

        mockMvc.perform(get("/projects/{pid}/agents/todos", projectId)
                        .cookie(cookie)
                        .param("usageStatementId", String.valueOf(statementId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.legal").isEmpty());
    }

    @Test
    void validate와_legal_동시에_조회된다() throws Exception {
        Cookie cookie = loginCookie(createUser("admin"));
        int projectId = createProject(cookie);
        int statementId = insertStatement(projectId);
        insertTodoLog(projectId, statementId, "safety-doc", "success", "hil", "누락 1건", null);
        insertTodoLog(projectId, statementId, "legal",      "success", "hil", "법령 위반 1건", null);

        mockMvc.perform(get("/projects/{pid}/agents/todos", projectId)
                        .cookie(cookie)
                        .param("usageStatementId", String.valueOf(statementId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.validate", hasSize(1)))
                .andExpect(jsonPath("$.data.legal.agentTypeCode").value("legal"));
    }

    @Test
    void usageStatementId_누락_시_400을_반환한다() throws Exception {
        Cookie cookie = loginCookie(createUser("admin"));
        int projectId = createProject(cookie);

        mockMvc.perform(get("/projects/{pid}/agents/todos", projectId)
                        .cookie(cookie))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 미인증_요청은_401을_반환한다() throws Exception {
        Cookie cookie = loginCookie(createUser("admin"));
        int projectId = createProject(cookie);
        int statementId = insertStatement(projectId);

        mockMvc.perform(get("/projects/{pid}/agents/todos", projectId)
                        .param("usageStatementId", String.valueOf(statementId)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 비담당자_user는_403을_반환한다() throws Exception {
        Cookie adminCookie = loginCookie(createUser("admin"));
        Cookie outsiderCookie = loginCookie(createUser("user"));
        int projectId = createProject(adminCookie);
        int statementId = insertStatement(projectId);

        mockMvc.perform(get("/projects/{pid}/agents/todos", projectId)
                        .cookie(outsiderCookie)
                        .param("usageStatementId", String.valueOf(statementId)))
                .andExpect(status().isForbidden());
    }

    @Test
    void 담당자_user는_todos를_조회할_수_있다() throws Exception {
        Cookie adminCookie = loginCookie(createUser("admin"));
        Map<String, String> user = createUser("user");
        Cookie userCookie = loginCookie(user);
        int userId = readUserId(user);
        int projectId = createProject(adminCookie);
        assign(adminCookie, projectId, userId);
        int statementId = insertStatement(projectId);

        mockMvc.perform(get("/projects/{pid}/agents/todos", projectId)
                        .cookie(userCookie)
                        .param("usageStatementId", String.valueOf(statementId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.validate").isArray());
    }

    @Test
    void review_completed_상태면_legal_hil이어도_null을_반환한다() throws Exception {
        Cookie cookie = loginCookie(createUser("admin"));
        int projectId = createProject(cookie);
        int statementId = insertStatement(projectId);
        updateStatementStatus(statementId, "review_completed");
        insertTodoLog(projectId, statementId, "legal", "success", "hil", "법령 위반 1건", null);

        mockMvc.perform(get("/projects/{pid}/agents/todos", projectId)
                        .cookie(cookie)
                        .param("usageStatementId", String.valueOf(statementId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.legal").isEmpty());
    }

    @Test
    void supplement_required_상태면_legal_hil이_반환된다() throws Exception {
        Cookie cookie = loginCookie(createUser("admin"));
        int projectId = createProject(cookie);
        int statementId = insertStatement(projectId);
        updateStatementStatus(statementId, "supplement_required");
        insertTodoLog(projectId, statementId, "legal", "success", "hil", "법령 위반 1건", null);

        mockMvc.perform(get("/projects/{pid}/agents/todos", projectId)
                        .cookie(cookie)
                        .param("usageStatementId", String.valueOf(statementId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.legal.agentTypeCode").value("legal"));
    }

    // ─── 위치 컨텍스트 필드 파싱 ──────────────────────────────────────────────

    @Test
    void safety_doc_todos에_위치_컨텍스트가_포함되면_응답에_그대로_반환된다() throws Exception {
        Cookie cookie = loginCookie(createUser("admin"));
        int projectId = createProject(cookie);
        int statementId = insertStatement(projectId);
        int itemId = insertItem(statementId);

        String todosJson = "[{" +
                "\"usage_statement_item_id\":" + itemId + "," +
                "\"category_code\":\"CAT_03\"," +
                "\"category_name\":\"안전시설비\"," +
                "\"usage_statement_item_name\":\"터널 환기덕트 안전시설 설치\"," +
                "\"title\":\"세금계산서\"," +
                "\"evidence_type_codes\":[\"tax_invoice\"]," +
                "\"reason\":\"터널 환기덕트 필수 증빙 누락: 세금계산서\"" +
                "}]";
        insertTodoLog(projectId, statementId, "safety-doc", "success", "hil",
                "필수 증빙 누락 항목 1건", todosJson);

        mockMvc.perform(get("/projects/{pid}/agents/todos", projectId)
                        .cookie(cookie)
                        .param("usageStatementId", String.valueOf(statementId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.validate[0].items[0].usageStatementItemId").value(itemId))
                .andExpect(jsonPath("$.data.validate[0].items[0].categoryCode").value("CAT_03"))
                .andExpect(jsonPath("$.data.validate[0].items[0].categoryName").value("안전시설비"))
                .andExpect(jsonPath("$.data.validate[0].items[0].usageStatementItemName").value("터널 환기덕트 안전시설 설치"))
                .andExpect(jsonPath("$.data.validate[0].items[0].title").value("세금계산서"))
                .andExpect(jsonPath("$.data.validate[0].items[0].evidenceTypeCodes[0]").value("tax_invoice"))
                .andExpect(jsonPath("$.data.validate[0].items[0].reason").value("터널 환기덕트 필수 증빙 누락: 세금계산서"));
    }

    @Test
    void safety_doc_todos에_증빙_유형이_복수이면_모두_반환된다() throws Exception {
        Cookie cookie = loginCookie(createUser("admin"));
        int projectId = createProject(cookie);
        int statementId = insertStatement(projectId);
        int itemId = insertItem(statementId);

        String todosJson = "[{" +
                "\"usage_statement_item_id\":" + itemId + "," +
                "\"category_code\":\"CAT_01\"," +
                "\"category_name\":\"안전보건관리자 임금\"," +
                "\"usage_statement_item_name\":\"안전관리자 임금\"," +
                "\"title\":\"세금계산서, 안전교육일지\"," +
                "\"evidence_type_codes\":[\"tax_invoice\",\"safety_education_log\"]," +
                "\"reason\":\"안전관리자 임금 필수 증빙 누락: 세금계산서, 안전교육일지\"" +
                "}]";
        insertTodoLog(projectId, statementId, "safety-doc", "success", "hil",
                "필수 증빙 누락 항목 1건", todosJson);

        mockMvc.perform(get("/projects/{pid}/agents/todos", projectId)
                        .cookie(cookie)
                        .param("usageStatementId", String.valueOf(statementId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.validate[0].items[0].evidenceTypeCodes", hasSize(2)))
                .andExpect(jsonPath("$.data.validate[0].items[0].evidenceTypeCodes[0]").value("tax_invoice"))
                .andExpect(jsonPath("$.data.validate[0].items[0].evidenceTypeCodes[1]").value("safety_education_log"));
    }

    @Test
    void link_todos에_위치_컨텍스트가_포함되면_응답에_그대로_반환된다() throws Exception {
        Cookie cookie = loginCookie(createUser("admin"));
        int projectId = createProject(cookie);
        int statementId = insertStatement(projectId);
        int itemId = insertItem(statementId);

        String todosJson = "[{" +
                "\"usage_statement_item_id\":" + itemId + "," +
                "\"category_code\":\"CAT_03\"," +
                "\"category_name\":\"안전시설비\"," +
                "\"usage_statement_item_name\":\"터널 환기덕트 안전시설 설치\"," +
                "\"title\":\"영수증/세금계산서\"," +
                "\"match_status\":\"review_needed\"," +
                "\"reason\":\"터널 환기덕트 증빙 매칭 검토 필요: review_needed\"" +
                "}]";
        insertTodoLog(projectId, statementId, "link", "success", "hil",
                "매칭 검토 보완 필요 1건", todosJson);

        mockMvc.perform(get("/projects/{pid}/agents/todos", projectId)
                        .cookie(cookie)
                        .param("usageStatementId", String.valueOf(statementId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.validate[0].agentTypeCode").value("link"))
                .andExpect(jsonPath("$.data.validate[0].items[0].categoryCode").value("CAT_03"))
                .andExpect(jsonPath("$.data.validate[0].items[0].categoryName").value("안전시설비"))
                .andExpect(jsonPath("$.data.validate[0].items[0].usageStatementItemName").value("터널 환기덕트 안전시설 설치"))
                .andExpect(jsonPath("$.data.validate[0].items[0].title").value("영수증/세금계산서"))
                .andExpect(jsonPath("$.data.validate[0].items[0].evidenceTypeCodes", hasSize(0)))
                .andExpect(jsonPath("$.data.validate[0].items[0].reason").value("터널 환기덕트 증빙 매칭 검토 필요: review_needed"));
    }

    @Test
    void 위치_컨텍스트_없는_기존_todos는_해당_필드가_null로_반환된다() throws Exception {
        Cookie cookie = loginCookie(createUser("admin"));
        int projectId = createProject(cookie);
        int statementId = insertStatement(projectId);
        int itemId = insertItem(statementId);

        String todosJson = "[{\"usage_statement_item_id\":" + itemId + ",\"reason\":\"필수 증빙 누락: 안전교육일지\"}]";
        insertTodoLog(projectId, statementId, "safety-doc", "success", "hil",
                "필수 증빙 누락 항목 1건", todosJson);

        mockMvc.perform(get("/projects/{pid}/agents/todos", projectId)
                        .cookie(cookie)
                        .param("usageStatementId", String.valueOf(statementId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.validate[0].items[0].categoryCode").isEmpty())
                .andExpect(jsonPath("$.data.validate[0].items[0].categoryName").isEmpty())
                .andExpect(jsonPath("$.data.validate[0].items[0].usageStatementItemName").isEmpty())
                .andExpect(jsonPath("$.data.validate[0].items[0].title").isEmpty())
                .andExpect(jsonPath("$.data.validate[0].items[0].evidenceTypeCodes", hasSize(0)))
                .andExpect(jsonPath("$.data.validate[0].items[0].reason").value("필수 증빙 누락: 안전교육일지"));
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

    private int insertItem(int statementId) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO service.usage_statement_items
                    (usage_statement_id, category_code, used_on, item_name, unit, quantity, unit_price, total_amount, page_no)
                VALUES (?, 'CAT_01', '2026-05-01', '안전관리자 임금', '월', 1, 500000, 500000, 1)
                RETURNING id
                """, Integer.class, statementId);
    }

    private void updateStatementStatus(int statementId, String statusCode) {
        jdbcTemplate.update("UPDATE service.usage_statements SET status_code = ? WHERE id = ?",
                statusCode, statementId);
    }

    private void insertTodoLog(int projectId, int statementId, String agentTypeCode,
            String statusCode, String resultCode, String reason, String todosJson) {
        String details = todosJson != null
                ? "{\"payload\":{\"todos\":" + todosJson + "}}"
                : "{\"payload\":{\"todos\":[]}}";
        jdbcTemplate.update("""
                INSERT INTO service.agent_logs
                    (project_id, usage_statement_id, agent_type_code, status_code, result_code, reason, details)
                VALUES (?, ?, ?, ?, ?, ?, ?::jsonb)
                """, projectId, statementId, agentTypeCode, statusCode, resultCode, reason, details);
    }
}
