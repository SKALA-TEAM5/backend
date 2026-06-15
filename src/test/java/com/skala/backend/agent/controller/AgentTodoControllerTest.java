package com.skala.backend.agent.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skala.backend.agent.service.TodoService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
    @Autowired TodoService todoService;

    // ─── GET /agents/todos (평탄화 리스트) ────────────────────────────────

    @Test
    void 로그_없으면_빈_배열을_반환한다() throws Exception {
        Cookie cookie = loginCookie(createUser("admin"));
        int projectId = createProject(cookie);
        int statementId = insertStatement(projectId);

        getTodos(cookie, projectId, statementId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test
    void safety_doc_hil_항목이_평탄화되어_반환된다() throws Exception {
        Cookie cookie = loginCookie(createUser("admin"));
        int projectId = createProject(cookie);
        int statementId = insertStatement(projectId);
        int itemId = insertItem(statementId);
        insertTodoLog(projectId, statementId, "safety-doc", "success", "hil",
                "필수 증빙 누락 항목 1건",
                "[{\"usage_statement_item_id\":" + itemId + ",\"reason\":\"필수 증빙 누락: 안전교육일지\"}]");
        todoService.refresh((long) statementId);

        getTodos(cookie, projectId, statementId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].agentTypeCode").value("safety-doc"))
                .andExpect(jsonPath("$.data[0].usageStatementItemId").value(itemId))
                .andExpect(jsonPath("$.data[0].reason").value("필수 증빙 누락: 안전교육일지"))
                .andExpect(jsonPath("$.data[0].confirmed").value(false))
                .andExpect(jsonPath("$.data[0].todoId").isNumber());
    }

    @Test
    void 항목_없는_실패_로그는_agent_단위_단건으로_보존된다() throws Exception {
        Cookie cookie = loginCookie(createUser("admin"));
        int projectId = createProject(cookie);
        int statementId = insertStatement(projectId);
        insertTodoLog(projectId, statementId, "link", "fail", "fail", "link Agent 실행 실패", null);
        todoService.refresh((long) statementId);

        getTodos(cookie, projectId, statementId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].agentTypeCode").value("link"))
                .andExpect(jsonPath("$.data[0].usageStatementItemId").isEmpty())
                .andExpect(jsonPath("$.data[0].reason").value("link Agent 실행 실패"));
    }

    @Test
    void success_agent는_포함되지_않는다() throws Exception {
        Cookie cookie = loginCookie(createUser("admin"));
        int projectId = createProject(cookie);
        int statementId = insertStatement(projectId);
        insertTodoLog(projectId, statementId, "safety-doc", "success", "success", "누락 없음", null);
        insertTodoLog(projectId, statementId, "link",       "success", "hil",     "매칭 검토 1건", null);
        todoService.refresh((long) statementId);

        getTodos(cookie, projectId, statementId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].agentTypeCode").value("link"));
    }

    @Test
    void validate와_legal이_한_리스트로_조회된다() throws Exception {
        Cookie cookie = loginCookie(createUser("admin"));
        int projectId = createProject(cookie);
        int statementId = insertStatement(projectId);
        insertTodoLog(projectId, statementId, "safety-doc", "success", "hil", "누락 1건", null);
        insertTodoLog(projectId, statementId, "legal",      "success", "hil", "법령 위반 1건", null);
        todoService.refresh((long) statementId);

        getTodos(cookie, projectId, statementId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)));
    }

    @Test
    void review_completed_상태면_legal_TODO는_제외된다() throws Exception {
        Cookie cookie = loginCookie(createUser("admin"));
        int projectId = createProject(cookie);
        int statementId = insertStatement(projectId);
        insertTodoLog(projectId, statementId, "legal", "success", "hil", "법령 위반 1건", null);
        todoService.refresh((long) statementId);
        updateStatementStatus(statementId, "review_completed");

        getTodos(cookie, projectId, statementId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test
    void supplement_required_상태면_legal_TODO가_포함된다() throws Exception {
        Cookie cookie = loginCookie(createUser("admin"));
        int projectId = createProject(cookie);
        int statementId = insertStatement(projectId);
        insertTodoLog(projectId, statementId, "legal", "success", "hil", "법령 위반 1건", null);
        todoService.refresh((long) statementId);
        updateStatementStatus(statementId, "supplement_required");

        getTodos(cookie, projectId, statementId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].agentTypeCode").value("legal"));
    }

    @Test
    void 위치_컨텍스트_필드가_평탄화되어_반환된다() throws Exception {
        Cookie cookie = loginCookie(createUser("admin"));
        int projectId = createProject(cookie);
        int statementId = insertStatement(projectId);
        int itemId = insertItem(statementId);
        String todosJson = "[{" +
                "\"usage_statement_item_id\":" + itemId + "," +
                "\"category_code\":\"CAT_03\"," +
                "\"category_name\":\"안전시설비\"," +
                "\"usage_statement_item_name\":\"터널 환기덕트 안전시설 설치\"," +
                "\"file_id\":77," +
                "\"reason\":\"터널 환기덕트 필수 증빙 누락: 세금계산서\"" +
                "}]";
        insertTodoLog(projectId, statementId, "safety-doc", "success", "hil", "필수 증빙 누락 항목 1건", todosJson);
        todoService.refresh((long) statementId);

        getTodos(cookie, projectId, statementId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].usageStatementItemId").value(itemId))
                .andExpect(jsonPath("$.data[0].categoryCode").value("CAT_03"))
                .andExpect(jsonPath("$.data[0].categoryName").value("안전시설비"))
                .andExpect(jsonPath("$.data[0].usageStatementItemName").value("터널 환기덕트 안전시설 설치"))
                .andExpect(jsonPath("$.data[0].fileId").value(77))
                .andExpect(jsonPath("$.data[0].reason").value("터널 환기덕트 필수 증빙 누락: 세금계산서"));
    }

    // evidence_types 시드(V4/V10) 전 유형에 대해 코드→한글 표시명 변환이 동작함을 보장한다.
    // 새 증빙 유형이 추가돼도 evidence_types 조인만으로 처리되어 프론트 하드코딩 매핑이 불필요함을 검증.
    @org.junit.jupiter.params.ParameterizedTest(name = "{0} → {1}")
    @org.junit.jupiter.params.provider.CsvSource({
            "tax_invoice,           전자세금계산서",
            "tax_invoice_confirm,   세금계산서 발급사실 조회",
            "receipt,               영수증",
            "transaction_statement, 거래명세표",
            "wage_statement,        임금명세서",
            "site_photo,            설치·시공 현황 사진",
            "item_photo,            물품 구매 현황 사진",
            "wearing_photo,         보호구 착용 상태 사진",
            "work_photo,            근무 현황 사진",
            "appointment_report,    선임신고서",
            "pay_stub,              급여명세서",
            "work_log,              업무일지",
            "daily_output_log,      출력일보",
            "inspection_log,        점검일지",
            "supply_ledger,         보호구 지급대장",
            "inventory_ledger,      입출고 관리대장",
            "edu_confirm,           교육 확인서",
            "edu_attendance,        교육 대상 명단",
            "transfer_confirm,      이체 확인증",
            "health_checkup_result, 건강검진 결과서",
            "health_checkup_contract, 건강검진 계약서",
            "tech_guidance_contract, 기술지도 계약서",
            "tech_guidance_report,  기술지도 결과 보고서",
            "tech_guidance_photo,   기술지도 점검 사진",
            "usage_statement,       안전관리비 사용내역서",
            "analysis_table,        안전관리비 분석표",
            "purchase_detail,       구매 내역서",
            "other_document,        기타 서류",
    })
    void 증빙_유형_코드는_한글_표시명으로_변환되어_반환된다(String evidenceTypeCode, String expectedName) throws Exception {
        Cookie cookie = loginCookie(createUser("admin"));
        int projectId = createProject(cookie);
        int statementId = insertStatement(projectId);
        int itemId = insertItem(statementId);
        String todosJson = "[{" +
                "\"usage_statement_item_id\":" + itemId + "," +
                "\"category_code\":\"CAT_03\"," +
                "\"category_name\":\"안전시설비\"," +
                "\"usage_statement_item_name\":\"터널 환기덕트 안전시설 설치\"," +
                "\"title\":\"" + evidenceTypeCode + "\"," +
                "\"evidence_type_codes\":[\"" + evidenceTypeCode + "\"]," +
                "\"reason\":\"필수 증빙 누락: " + evidenceTypeCode + "\"" +
                "}]";
        insertTodoLog(projectId, statementId, "safety-doc", "success", "hil", "필수 증빙 누락 항목 1건", todosJson);
        todoService.refresh((long) statementId);

        getTodos(cookie, projectId, statementId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].evidenceTypeCode").value(evidenceTypeCode))
                .andExpect(jsonPath("$.data[0].evidenceTypeName").value(expectedName));
    }

    @Test
    void 증빙_유형이_여러개면_코드별로_분할되어_각자_표시명을_가진다() throws Exception {
        Cookie cookie = loginCookie(createUser("admin"));
        int projectId = createProject(cookie);
        int statementId = insertStatement(projectId);
        int itemId = insertItem(statementId);
        String todosJson = "[{" +
                "\"usage_statement_item_id\":" + itemId + "," +
                "\"category_code\":\"CAT_03\",\"category_name\":\"안전시설비\"," +
                "\"usage_statement_item_name\":\"터널 환기덕트 안전시설 설치\"," +
                "\"title\":\"tax_invoice, wearing_photo\"," +
                "\"evidence_type_codes\":[\"tax_invoice\",\"wearing_photo\"]," +
                "\"reason\":\"필수 증빙 누락: tax_invoice, wearing_photo\"" +
                "}]";
        insertTodoLog(projectId, statementId, "safety-doc", "success", "hil", "필수 증빙 누락 항목 2건", todosJson);
        todoService.refresh((long) statementId);

        // 코드별 분할 → evidence_type_code 알파벳 정렬 무관, 두 표시명이 모두 존재해야 한다
        getTodos(cookie, projectId, statementId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[?(@.evidenceTypeCode == 'tax_invoice')].evidenceTypeName")
                        .value(org.hamcrest.Matchers.hasItem("전자세금계산서")))
                .andExpect(jsonPath("$.data[?(@.evidenceTypeCode == 'wearing_photo')].evidenceTypeName")
                        .value(org.hamcrest.Matchers.hasItem("보호구 착용 상태 사진")));
    }

    @Test
    void 미등록_증빙_유형_코드는_표시명_대신_코드를_그대로_반환한다() throws Exception {
        Cookie cookie = loginCookie(createUser("admin"));
        int projectId = createProject(cookie);
        int statementId = insertStatement(projectId);
        int itemId = insertItem(statementId);
        String todosJson = "[{" +
                "\"usage_statement_item_id\":" + itemId + "," +
                "\"category_code\":\"CAT_03\",\"category_name\":\"안전시설비\"," +
                "\"usage_statement_item_name\":\"터널 환기덕트 안전시설 설치\"," +
                "\"title\":\"brand_new_code\"," +
                "\"evidence_type_codes\":[\"brand_new_code\"]," +
                "\"reason\":\"필수 증빙 누락: brand_new_code\"" +
                "}]";
        insertTodoLog(projectId, statementId, "safety-doc", "success", "hil", "필수 증빙 누락 항목 1건", todosJson);
        todoService.refresh((long) statementId);

        // evidence_types 에 없는 코드는 COALESCE 폴백으로 코드 자체가 표시명이 된다
        getTodos(cookie, projectId, statementId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].evidenceTypeCode").value("brand_new_code"))
                .andExpect(jsonPath("$.data[0].evidenceTypeName").value("brand_new_code"));
    }

    @Test
    void 증빙_유형이_없는_TODO는_표시명도_null로_반환된다() throws Exception {
        Cookie cookie = loginCookie(createUser("admin"));
        int projectId = createProject(cookie);
        int statementId = insertStatement(projectId);
        int itemId = insertItem(statementId);
        insertTodoLog(projectId, statementId, "link", "success", "hil", "매칭 검토 필요",
                "[{\"usage_statement_item_id\":" + itemId + ",\"reason\":\"증빙 매칭 검토 필요\"}]");
        todoService.refresh((long) statementId);

        getTodos(cookie, projectId, statementId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].evidenceTypeCode").isEmpty())
                .andExpect(jsonPath("$.data[0].evidenceTypeName").isEmpty());
    }

    @Test
    void 위치_컨텍스트_없으면_해당_필드는_null로_반환된다() throws Exception {
        Cookie cookie = loginCookie(createUser("admin"));
        int projectId = createProject(cookie);
        int statementId = insertStatement(projectId);
        int itemId = insertItem(statementId);
        insertTodoLog(projectId, statementId, "safety-doc", "success", "hil", "필수 증빙 누락 항목 1건",
                "[{\"usage_statement_item_id\":" + itemId + ",\"reason\":\"필수 증빙 누락: 안전교육일지\"}]");
        todoService.refresh((long) statementId);

        getTodos(cookie, projectId, statementId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].categoryCode").isEmpty())
                .andExpect(jsonPath("$.data[0].categoryName").isEmpty())
                .andExpect(jsonPath("$.data[0].usageStatementItemName").isEmpty())
                .andExpect(jsonPath("$.data[0].fileId").isEmpty())
                .andExpect(jsonPath("$.data[0].reason").value("필수 증빙 누락: 안전교육일지"));
    }

    @Test
    void usageStatementId_누락_시_400을_반환한다() throws Exception {
        Cookie cookie = loginCookie(createUser("admin"));
        int projectId = createProject(cookie);

        mockMvc.perform(get("/projects/{pid}/agents/todos", projectId).cookie(cookie))
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

        getTodos(userCookie, projectId, statementId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    // ─── PATCH /agents/todos/{todoId}/confirm ─────────────────────────────

    @Test
    void todo를_확인하면_confirmed_true로_조회된다() throws Exception {
        Cookie cookie = loginCookie(createUser("admin"));
        int projectId = createProject(cookie);
        int statementId = insertStatement(projectId);
        int itemId = insertItem(statementId);
        insertTodoLog(projectId, statementId, "safety-doc", "success", "hil", "누락 1건",
                "[{\"usage_statement_item_id\":" + itemId + ",\"reason\":\"필수 증빙 누락: 안전교육일지\"}]");
        todoService.refresh((long) statementId);
        long todoId = firstTodoId(statementId);

        confirmTodo(cookie, projectId, todoId, true);

        getTodos(cookie, projectId, statementId)
                .andExpect(jsonPath("$.data[0].confirmed").value(true));
    }

    @Test
    void 확인_해제하면_confirmed_false로_조회된다() throws Exception {
        Cookie cookie = loginCookie(createUser("admin"));
        int projectId = createProject(cookie);
        int statementId = insertStatement(projectId);
        insertTodoLog(projectId, statementId, "safety-doc", "success", "hil", "누락 1건", null);
        todoService.refresh((long) statementId);
        long todoId = firstTodoId(statementId);

        confirmTodo(cookie, projectId, todoId, true);
        confirmTodo(cookie, projectId, todoId, false);

        getTodos(cookie, projectId, statementId)
                .andExpect(jsonPath("$.data[0].confirmed").value(false));
    }

    @Test
    void reason이_바뀌면_확인이_자동_해제된다() throws Exception {
        Cookie cookie = loginCookie(createUser("admin"));
        int projectId = createProject(cookie);
        int statementId = insertStatement(projectId);
        int itemId = insertItem(statementId);
        insertTodoLog(projectId, statementId, "safety-doc", "success", "hil", "누락 1건",
                "[{\"usage_statement_item_id\":" + itemId + ",\"reason\":\"필수 증빙 누락: 안전교육일지\"}]");
        todoService.refresh((long) statementId);
        confirmTodo(cookie, projectId, firstTodoId(statementId), true);

        // agent 재실행으로 같은 항목의 reason이 바뀜 → 새 TODO로 재생성
        updateTodoLogDetails(statementId, "safety-doc",
                "[{\"usage_statement_item_id\":" + itemId + ",\"reason\":\"필수 증빙 누락: 세금계산서\"}]");
        todoService.refresh((long) statementId);

        getTodos(cookie, projectId, statementId)
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].reason").value("필수 증빙 누락: 세금계산서"))
                .andExpect(jsonPath("$.data[0].confirmed").value(false));
    }

    @Test
    void 동일_내용으로_재생성되면_확인이_유지된다() throws Exception {
        Cookie cookie = loginCookie(createUser("admin"));
        int projectId = createProject(cookie);
        int statementId = insertStatement(projectId);
        int itemId = insertItem(statementId);
        String todosJson = "[{\"usage_statement_item_id\":" + itemId + ",\"reason\":\"필수 증빙 누락: 안전교육일지\"}]";
        insertTodoLog(projectId, statementId, "safety-doc", "success", "hil", "누락 1건", todosJson);
        todoService.refresh((long) statementId);
        confirmTodo(cookie, projectId, firstTodoId(statementId), true);

        updateTodoLogDetails(statementId, "safety-doc", todosJson);
        todoService.refresh((long) statementId);

        getTodos(cookie, projectId, statementId)
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].confirmed").value(true));
    }

    @Test
    void 다른_프로젝트의_todo면_400을_반환한다() throws Exception {
        Cookie cookie = loginCookie(createUser("admin"));
        int projectA = createProject(cookie);
        int projectB = createProject(cookie);
        int statementB = insertStatement(projectB);
        insertTodoLog(projectB, statementB, "safety-doc", "success", "hil", "누락 1건", null);
        todoService.refresh((long) statementB);
        long todoIdB = firstTodoId(statementB);

        mockMvc.perform(patch("/projects/{pid}/agents/todos/{tid}/confirm", projectA, todoIdB)
                        .cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("confirmed", true))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 존재하지_않는_todo면_404를_반환한다() throws Exception {
        Cookie cookie = loginCookie(createUser("admin"));
        int projectId = createProject(cookie);

        mockMvc.perform(patch("/projects/{pid}/agents/todos/{tid}/confirm", projectId, 999999)
                        .cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("confirmed", true))))
                .andExpect(status().isNotFound());
    }

    // ─── 실제 agent_logs.details 페이로드 파싱 ─────────────────────────────

    @Test
    void 실제_vision_link_safety_doc_페이로드를_정확히_파싱한다() throws Exception {
        Cookie cookie = loginCookie(createUser("admin"));
        int projectId = createProject(cookie);
        int statementId = insertStatement(projectId);

        // vision: payload.todos 1건 + vision_response.todos 중복 + results/photos 노이즈
        insertRawLog(projectId, statementId, "vision", "success", "hil", "현장사진 2건 중 1건 보완 필요", """
                {
                  "event": "vision_completed",
                  "summary": "현장사진 검토 완료",
                  "payload": {
                    "todos": [
                      {
                        "reason": "안전모가 확인되지 않았습니다.",
                        "file_id": 108,
                        "category_code": "CAT_03",
                        "category_name": "보호구 등",
                        "target_equipment": "safety_helmet",
                        "usage_statement_item_id": 109,
                        "usage_statement_item_name": "안전모 (ABS 산업용)"
                      }
                    ],
                    "photos": [ {"file_id": 108, "evidence_type_code": "site_photo"} ],
                    "vision_response": {
                      "todos": [ {"reason": "안전모가 확인되지 않았습니다.", "file_id": 108, "usage_statement_item_id": 109} ],
                      "result_code": "hil"
                    }
                  },
                  "results": [ {"status": "needs_review", "file_id": 108} ]
                }
                """);

        // link: file_id/category 없음
        insertRawLog(projectId, statementId, "link", "success", "hil", "매칭 검토 필요 1건", """
                {
                  "event": "link_completed",
                  "summary": "매칭 검토 필요 1건",
                  "payload": {
                    "summary": {"matched": 3, "review_needed": 1, "unmatched": 0},
                    "match_results": [ {"line_id": "10", "match_status": "review_needed", "receipt_id": "r_001"} ],
                    "todos": [
                      {
                        "usage_statement_item_id": 10,
                        "title": "영수증/세금계산서",
                        "match_status": "review_needed",
                        "reason": "안전화 구매 증빙 매칭 검토 필요: review_needed"
                      }
                    ]
                  }
                }
                """);

        // safety_docs: todos 2건 (CAT_01, CAT_02)
        insertRawLog(projectId, statementId, "safety-doc", "success", "hil", "필수 증빙 누락 항목 2건", """
                {
                  "event": "safety_doc_completed",
                  "payload": {
                    "todos": [
                      {
                        "title": "appointment_report, pay_stub, wage_statement",
                        "reason": "안전관리자 인건비 필수 증빙 누락: appointment_report, pay_stub, wage_statement",
                        "category_code": "CAT_01",
                        "category_name": "안전, 보건관리자 임금 등",
                        "evidence_type_codes": ["appointment_report", "pay_stub", "wage_statement"],
                        "usage_statement_item_id": 104,
                        "usage_statement_item_name": "안전관리자 인건비"
                      },
                      {
                        "title": "site_photo, tax_invoice",
                        "reason": "추락방지용 안전난간 설치 필수 증빙 누락: site_photo, tax_invoice",
                        "category_code": "CAT_02",
                        "category_name": "안전시설비 등",
                        "evidence_type_codes": ["site_photo", "tax_invoice"],
                        "usage_statement_item_id": 106,
                        "usage_statement_item_name": "추락방지용 안전난간 설치"
                      }
                    ]
                  }
                }
                """);

        todoService.refresh((long) statementId);

        // vision 1 + link 1 + safety-doc 5 (CAT_01의 코드 3개 + CAT_02의 코드 2개로 분할) = 7
        // (vision_response.todos 중복 미포함)
        getTodos(cookie, projectId, statementId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(7)))
                // vision: file_id/category/item 모두 매핑
                .andExpect(jsonPath("$.data[?(@.agentTypeCode == 'vision')].fileId").value(108))
                .andExpect(jsonPath("$.data[?(@.agentTypeCode == 'vision')].usageStatementItemId").value(109))
                .andExpect(jsonPath("$.data[?(@.agentTypeCode == 'vision')].categoryCode").value("CAT_03"))
                .andExpect(jsonPath("$.data[?(@.agentTypeCode == 'vision')].usageStatementItemName").value("안전모 (ABS 산업용)"))
                // link: file_id/category 없음 → null, item만 존재
                .andExpect(jsonPath("$.data[?(@.agentTypeCode == 'link')].usageStatementItemId").value(10))
                .andExpect(jsonPath("$.data[?(@.agentTypeCode == 'link')].fileId")
                        .value(org.hamcrest.Matchers.contains(org.hamcrest.Matchers.nullValue())))
                .andExpect(jsonPath("$.data[?(@.agentTypeCode == 'link')].categoryCode")
                        .value(org.hamcrest.Matchers.contains(org.hamcrest.Matchers.nullValue())))
                // safety-doc: evidence_type_codes 가 보완 작업(증빙 유형)마다 별도 TODO로 분할된다
                .andExpect(jsonPath("$.data[?(@.agentTypeCode == 'safety-doc')]", hasSize(5)))
                // CAT_01: 3개 코드 → 3건, reason 이 코드 단위로 분리
                .andExpect(jsonPath("$.data[?(@.categoryCode == 'CAT_01')]", hasSize(3)))
                .andExpect(jsonPath("$.data[?(@.categoryCode == 'CAT_01')].reason")
                        .value(org.hamcrest.Matchers.containsInAnyOrder(
                                "안전관리자 인건비 필수 증빙 누락: appointment_report",
                                "안전관리자 인건비 필수 증빙 누락: pay_stub",
                                "안전관리자 인건비 필수 증빙 누락: wage_statement")))
                // CAT_02: 2개 코드 → 2건
                .andExpect(jsonPath("$.data[?(@.categoryCode == 'CAT_02')]", hasSize(2)))
                .andExpect(jsonPath("$.data[?(@.categoryCode == 'CAT_02')].reason")
                        .value(org.hamcrest.Matchers.containsInAnyOrder(
                                "추락방지용 안전난간 설치 필수 증빙 누락: site_photo",
                                "추락방지용 안전난간 설치 필수 증빙 누락: tax_invoice")));
    }

    // ─── fixtures ─────────────────────────────────────────────────────────

    private void insertRawLog(int projectId, int statementId, String agentTypeCode,
            String statusCode, String resultCode, String reason, String detailsJson) {
        jdbcTemplate.update("""
                INSERT INTO service.agent_logs
                    (project_id, usage_statement_id, agent_type_code, status_code, result_code, reason, details)
                VALUES (?, ?, ?, ?, ?, ?, ?::jsonb)
                """, projectId, statementId, agentTypeCode, statusCode, resultCode, reason, detailsJson);
    }

    private org.springframework.test.web.servlet.ResultActions getTodos(Cookie cookie, int projectId, int statementId)
            throws Exception {
        return mockMvc.perform(get("/projects/{pid}/agents/todos", projectId)
                .cookie(cookie)
                .param("usageStatementId", String.valueOf(statementId)));
    }

    private void confirmTodo(Cookie cookie, int projectId, long todoId, boolean confirmed) throws Exception {
        mockMvc.perform(patch("/projects/{pid}/agents/todos/{tid}/confirm", projectId, todoId)
                        .cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("confirmed", confirmed))))
                .andExpect(status().isOk());
    }

    private long firstTodoId(int statementId) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM service.todos WHERE usage_statement_id = ? ORDER BY id LIMIT 1",
                Long.class, statementId);
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

    private void updateTodoLogDetails(int statementId, String agentTypeCode, String todosJson) {
        String details = "{\"payload\":{\"todos\":" + todosJson + "}}";
        jdbcTemplate.update("""
                UPDATE service.agent_logs SET details = ?::jsonb
                WHERE usage_statement_id = ? AND agent_type_code = ? AND usage_statement_item_id IS NULL
                """, details, statementId, agentTypeCode);
    }
}
