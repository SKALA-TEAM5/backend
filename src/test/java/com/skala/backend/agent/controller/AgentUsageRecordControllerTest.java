package com.skala.backend.agent.controller;

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

import org.junit.jupiter.api.BeforeEach;

import java.math.BigDecimal;
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
class AgentUsageRecordControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM service.agent_usage_records");
    }

    // ─── GET /usage-records/by-user ───────────────────────────────────────

    @Test
    void admin은_모든_사용자의_사용량을_집계해서_반환한다() throws Exception {
        Map<String, String> admin = createUser("admin");
        Map<String, String> user1 = createUser("user");
        Map<String, String> user2 = createUser("user");
        Cookie adminCookie = loginCookie(admin);
        int adminId = readUserId(admin);
        int user1Id = readUserId(user1);
        int user2Id = readUserId(user2);
        int projectId = createProject(adminCookie);

        insertUsageRecord(adminId, projectId, "legal",      100, 50, "0.01000000");
        insertUsageRecord(user1Id, projectId, "safety-doc", 200, 80, "0.02000000");
        insertUsageRecord(user2Id, projectId, "vision",     300, 100, "0.03000000");

        mockMvc.perform(get("/usage-records/by-user").cookie(adminCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(3)));
    }

    @Test
    void by_user_응답에_사용자명이_포함된다() throws Exception {
        Map<String, String> admin = createUser("admin");
        Cookie adminCookie = loginCookie(admin);
        int adminId = readUserId(admin);
        int projectId = createProject(adminCookie);

        insertUsageRecord(adminId, projectId, "legal", 100, 50, "0.01000000");

        mockMvc.perform(get("/usage-records/by-user").cookie(adminCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].userName").value("홍길동"));
    }

    @Test
    void user는_본인_데이터만_반환된다() throws Exception {
        Map<String, String> admin = createUser("admin");
        Map<String, String> user1 = createUser("user");
        Map<String, String> user2 = createUser("user");
        Cookie adminCookie = loginCookie(admin);
        Cookie user1Cookie = loginCookie(user1);
        int user1Id = readUserId(user1);
        int user2Id = readUserId(user2);
        int projectId = createProject(adminCookie);

        insertUsageRecord(user1Id, projectId, "legal",      100, 50, "0.01000000");
        insertUsageRecord(user2Id, projectId, "safety-doc", 200, 80, "0.02000000");

        mockMvc.perform(get("/usage-records/by-user").cookie(user1Cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].userId").value(user1Id));
    }

    @Test
    void user가_다른_userId를_전달해도_본인_데이터만_반환된다() throws Exception {
        Map<String, String> admin = createUser("admin");
        Map<String, String> user1 = createUser("user");
        Map<String, String> user2 = createUser("user");
        Cookie adminCookie = loginCookie(admin);
        Cookie user1Cookie = loginCookie(user1);
        int user1Id = readUserId(user1);
        int user2Id = readUserId(user2);
        int projectId = createProject(adminCookie);

        insertUsageRecord(user1Id, projectId, "legal",      100, 50, "0.01000000");
        insertUsageRecord(user2Id, projectId, "safety-doc", 200, 80, "0.02000000");

        mockMvc.perform(get("/usage-records/by-user").cookie(user1Cookie)
                        .param("userId", String.valueOf(user2Id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].userId").value(user1Id));
    }

    @Test
    void admin이_userId_필터로_특정_사용자만_조회할_수_있다() throws Exception {
        Map<String, String> admin = createUser("admin");
        Map<String, String> user1 = createUser("user");
        Map<String, String> user2 = createUser("user");
        Cookie adminCookie = loginCookie(admin);
        int user1Id = readUserId(user1);
        int user2Id = readUserId(user2);
        int projectId = createProject(adminCookie);

        insertUsageRecord(user1Id, projectId, "legal",      100, 50, "0.01000000");
        insertUsageRecord(user2Id, projectId, "safety-doc", 200, 80, "0.02000000");

        mockMvc.perform(get("/usage-records/by-user").cookie(adminCookie)
                        .param("userId", String.valueOf(user1Id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].userId").value(user1Id));
    }

    @Test
    void by_user_projectId_필터로_특정_프로젝트만_집계된다() throws Exception {
        Map<String, String> admin = createUser("admin");
        Cookie adminCookie = loginCookie(admin);
        int adminId = readUserId(admin);
        int projectA = createProject(adminCookie);
        int projectB = createProject(adminCookie);

        insertUsageRecord(adminId, projectA, "legal", 100, 50, "0.01000000");
        insertUsageRecord(adminId, projectB, "legal", 200, 80, "0.02000000");

        mockMvc.perform(get("/usage-records/by-user").cookie(adminCookie)
                        .param("projectId", String.valueOf(projectA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].inputTokens").value(100));
    }

    @Test
    void by_user_agentTypeCode_필터로_특정_에이전트만_집계된다() throws Exception {
        Map<String, String> admin = createUser("admin");
        Cookie adminCookie = loginCookie(admin);
        int adminId = readUserId(admin);
        int projectId = createProject(adminCookie);

        insertUsageRecord(adminId, projectId, "legal",      100, 50, "0.01000000");
        insertUsageRecord(adminId, projectId, "safety-doc", 200, 80, "0.02000000");

        mockMvc.perform(get("/usage-records/by-user").cookie(adminCookie)
                        .param("agentTypeCode", "legal"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].inputTokens").value(100));
    }

    @Test
    void by_user_from_to_날짜_필터가_동작한다() throws Exception {
        Map<String, String> admin = createUser("admin");
        Cookie adminCookie = loginCookie(admin);
        int adminId = readUserId(admin);
        int projectId = createProject(adminCookie);

        insertUsageRecordAt(adminId, projectId, "legal", 100, 50, "0.01000000", "2026-05-10T00:00:00Z");
        insertUsageRecordAt(adminId, projectId, "legal", 200, 80, "0.02000000", "2026-05-20T00:00:00Z");

        mockMvc.perform(get("/usage-records/by-user").cookie(adminCookie)
                        .param("from", "2026-05-15")
                        .param("to", "2026-05-25"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].inputTokens").value(200));
    }

    @Test
    void by_user_데이터가_없으면_빈_배열을_반환한다() throws Exception {
        Cookie adminCookie = loginCookie(createUser("admin"));

        mockMvc.perform(get("/usage-records/by-user").cookie(adminCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test
    void by_user_미인증_요청은_401을_반환한다() throws Exception {
        mockMvc.perform(get("/usage-records/by-user"))
                .andExpect(status().isUnauthorized());
    }

    // ─── GET /usage-records/by-project ───────────────────────────────────

    @Test
    void admin은_모든_프로젝트의_사용량을_집계한다() throws Exception {
        Map<String, String> admin = createUser("admin");
        Cookie adminCookie = loginCookie(admin);
        int adminId = readUserId(admin);
        int projectA = createProject(adminCookie);
        int projectB = createProject(adminCookie);

        insertUsageRecord(adminId, projectA, "legal",      100, 50, "0.01000000");
        insertUsageRecord(adminId, projectB, "safety-doc", 200, 80, "0.02000000");

        mockMvc.perform(get("/usage-records/by-project").cookie(adminCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)));
    }

    @Test
    void by_project_응답에_프로젝트명이_포함된다() throws Exception {
        Map<String, String> admin = createUser("admin");
        Cookie adminCookie = loginCookie(admin);
        int adminId = readUserId(admin);
        int projectId = createProject(adminCookie);

        insertUsageRecord(adminId, projectId, "legal", 100, 50, "0.01000000");

        mockMvc.perform(get("/usage-records/by-project").cookie(adminCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].projectName").isNotEmpty())
                .andExpect(jsonPath("$.data[0].projectId").value(projectId));
    }

    @Test
    void by_project_user는_본인이_실행한_프로젝트만_반환된다() throws Exception {
        Map<String, String> admin = createUser("admin");
        Map<String, String> user1 = createUser("user");
        Map<String, String> user2 = createUser("user");
        Cookie adminCookie = loginCookie(admin);
        Cookie user1Cookie = loginCookie(user1);
        int user1Id = readUserId(user1);
        int user2Id = readUserId(user2);
        int projectA = createProject(adminCookie);
        int projectB = createProject(adminCookie);

        insertUsageRecord(user1Id, projectA, "legal",      100, 50, "0.01000000");
        insertUsageRecord(user2Id, projectB, "safety-doc", 200, 80, "0.02000000");

        mockMvc.perform(get("/usage-records/by-project").cookie(user1Cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].projectId").value(projectA));
    }

    @Test
    void by_project_같은_프로젝트의_토큰이_합산된다() throws Exception {
        Map<String, String> admin = createUser("admin");
        Cookie adminCookie = loginCookie(admin);
        int adminId = readUserId(admin);
        int projectId = createProject(adminCookie);

        insertUsageRecord(adminId, projectId, "legal",      100, 50, "0.01000000");
        insertUsageRecord(adminId, projectId, "safety-doc", 200, 80, "0.02000000");

        mockMvc.perform(get("/usage-records/by-project").cookie(adminCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].inputTokens").value(300))
                .andExpect(jsonPath("$.data[0].outputTokens").value(130))
                .andExpect(jsonPath("$.data[0].callCount").value(2));
    }

    @Test
    void by_project_미인증_요청은_401을_반환한다() throws Exception {
        mockMvc.perform(get("/usage-records/by-project"))
                .andExpect(status().isUnauthorized());
    }

    // ─── GET /usage-records/by-agent ─────────────────────────────────────

    @Test
    void 에이전트_유형별로_집계된다() throws Exception {
        Map<String, String> admin = createUser("admin");
        Cookie adminCookie = loginCookie(admin);
        int adminId = readUserId(admin);
        int projectId = createProject(adminCookie);

        insertUsageRecord(adminId, projectId, "legal",      100, 50,  "0.01000000");
        insertUsageRecord(adminId, projectId, "legal",      200, 80,  "0.02000000");
        insertUsageRecord(adminId, projectId, "safety-doc", 300, 100, "0.03000000");

        mockMvc.perform(get("/usage-records/by-agent").cookie(adminCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)));
    }

    @Test
    void 같은_에이전트의_토큰이_합산된다() throws Exception {
        Map<String, String> admin = createUser("admin");
        Cookie adminCookie = loginCookie(admin);
        int adminId = readUserId(admin);
        int projectId = createProject(adminCookie);

        insertUsageRecord(adminId, projectId, "legal", 100, 50, "0.01000000");
        insertUsageRecord(adminId, projectId, "legal", 200, 80, "0.02000000");

        mockMvc.perform(get("/usage-records/by-agent").cookie(adminCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].agentTypeCode").value("legal"))
                .andExpect(jsonPath("$.data[0].inputTokens").value(300))
                .andExpect(jsonPath("$.data[0].outputTokens").value(130))
                .andExpect(jsonPath("$.data[0].callCount").value(2));
    }

    @Test
    void by_agent_user는_본인_실행_데이터만_집계된다() throws Exception {
        Map<String, String> admin = createUser("admin");
        Map<String, String> user1 = createUser("user");
        Map<String, String> user2 = createUser("user");
        Cookie adminCookie = loginCookie(admin);
        Cookie user1Cookie = loginCookie(user1);
        int user1Id = readUserId(user1);
        int user2Id = readUserId(user2);
        int projectId = createProject(adminCookie);

        insertUsageRecord(user1Id, projectId, "legal",      100, 50, "0.01000000");
        insertUsageRecord(user2Id, projectId, "safety-doc", 200, 80, "0.02000000");

        mockMvc.perform(get("/usage-records/by-agent").cookie(user1Cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].agentTypeCode").value("legal"));
    }

    @Test
    void by_agent_projectId_필터가_동작한다() throws Exception {
        Map<String, String> admin = createUser("admin");
        Cookie adminCookie = loginCookie(admin);
        int adminId = readUserId(admin);
        int projectA = createProject(adminCookie);
        int projectB = createProject(adminCookie);

        insertUsageRecord(adminId, projectA, "legal",      100, 50, "0.01000000");
        insertUsageRecord(adminId, projectB, "safety-doc", 200, 80, "0.02000000");

        mockMvc.perform(get("/usage-records/by-agent").cookie(adminCookie)
                        .param("projectId", String.valueOf(projectA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].agentTypeCode").value("legal"));
    }

    @Test
    void by_agent_미인증_요청은_401을_반환한다() throws Exception {
        mockMvc.perform(get("/usage-records/by-agent"))
                .andExpect(status().isUnauthorized());
    }

    // ─── GET /usage-records/by-month ─────────────────────────────────────

    @Test
    void 월별로_집계되어_오름차순으로_반환된다() throws Exception {
        Map<String, String> admin = createUser("admin");
        Cookie adminCookie = loginCookie(admin);
        int adminId = readUserId(admin);
        int projectId = createProject(adminCookie);

        insertUsageRecordAt(adminId, projectId, "legal", 100, 50,  "0.01000000", "2026-04-10T00:00:00Z");
        insertUsageRecordAt(adminId, projectId, "legal", 200, 80,  "0.02000000", "2026-04-25T00:00:00Z");
        insertUsageRecordAt(adminId, projectId, "legal", 300, 100, "0.03000000", "2026-05-15T00:00:00Z");

        mockMvc.perform(get("/usage-records/by-month").cookie(adminCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0].month").value("2026-04"))
                .andExpect(jsonPath("$.data[0].inputTokens").value(300))
                .andExpect(jsonPath("$.data[0].callCount").value(2))
                .andExpect(jsonPath("$.data[1].month").value("2026-05"))
                .andExpect(jsonPath("$.data[1].inputTokens").value(300));
    }

    @Test
    void by_month_from_to_날짜_필터가_동작한다() throws Exception {
        Map<String, String> admin = createUser("admin");
        Cookie adminCookie = loginCookie(admin);
        int adminId = readUserId(admin);
        int projectId = createProject(adminCookie);

        insertUsageRecordAt(adminId, projectId, "legal", 100, 50, "0.01000000", "2026-03-01T00:00:00Z");
        insertUsageRecordAt(adminId, projectId, "legal", 200, 80, "0.02000000", "2026-05-01T00:00:00Z");
        insertUsageRecordAt(adminId, projectId, "legal", 300, 100, "0.03000000", "2026-07-01T00:00:00Z");

        mockMvc.perform(get("/usage-records/by-month").cookie(adminCookie)
                        .param("from", "2026-04-01")
                        .param("to", "2026-06-30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].month").value("2026-05"));
    }

    @Test
    void by_month_user는_본인_데이터만_조회된다() throws Exception {
        Map<String, String> admin = createUser("admin");
        Map<String, String> user1 = createUser("user");
        Map<String, String> user2 = createUser("user");
        Cookie adminCookie = loginCookie(admin);
        Cookie user1Cookie = loginCookie(user1);
        int user1Id = readUserId(user1);
        int user2Id = readUserId(user2);
        int projectId = createProject(adminCookie);

        insertUsageRecordAt(user1Id, projectId, "legal", 100, 50, "0.01000000", "2026-05-01T00:00:00Z");
        insertUsageRecordAt(user2Id, projectId, "legal", 200, 80, "0.02000000", "2026-05-01T00:00:00Z");

        mockMvc.perform(get("/usage-records/by-month").cookie(user1Cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].inputTokens").value(100));
    }

    @Test
    void by_month_미인증_요청은_401을_반환한다() throws Exception {
        mockMvc.perform(get("/usage-records/by-month"))
                .andExpect(status().isUnauthorized());
    }

    // ─── GET /usage-records/by-date ──────────────────────────────────────

    @Test
    void 날짜별로_집계되어_오름차순으로_반환된다() throws Exception {
        Map<String, String> admin = createUser("admin");
        Cookie adminCookie = loginCookie(admin);
        int adminId = readUserId(admin);
        int projectId = createProject(adminCookie);

        insertUsageRecordAt(adminId, projectId, "legal", 100, 50,  "0.01000000", "2026-05-10T00:00:00Z");
        insertUsageRecordAt(adminId, projectId, "legal", 150, 60,  "0.01500000", "2026-05-10T12:00:00Z");
        insertUsageRecordAt(adminId, projectId, "legal", 200, 80,  "0.02000000", "2026-05-20T00:00:00Z");

        mockMvc.perform(get("/usage-records/by-date").cookie(adminCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0].date").value("2026-05-10"))
                .andExpect(jsonPath("$.data[0].inputTokens").value(250))
                .andExpect(jsonPath("$.data[0].callCount").value(2))
                .andExpect(jsonPath("$.data[1].date").value("2026-05-20"));
    }

    @Test
    void by_date_from_날짜_이후만_조회된다() throws Exception {
        Map<String, String> admin = createUser("admin");
        Cookie adminCookie = loginCookie(admin);
        int adminId = readUserId(admin);
        int projectId = createProject(adminCookie);

        insertUsageRecordAt(adminId, projectId, "legal", 100, 50, "0.01000000", "2026-05-10T00:00:00Z");
        insertUsageRecordAt(adminId, projectId, "legal", 200, 80, "0.02000000", "2026-05-20T00:00:00Z");

        mockMvc.perform(get("/usage-records/by-date").cookie(adminCookie)
                        .param("from", "2026-05-15"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].date").value("2026-05-20"));
    }

    @Test
    void by_date_to_날짜_이전만_조회된다() throws Exception {
        Map<String, String> admin = createUser("admin");
        Cookie adminCookie = loginCookie(admin);
        int adminId = readUserId(admin);
        int projectId = createProject(adminCookie);

        insertUsageRecordAt(adminId, projectId, "legal", 100, 50, "0.01000000", "2026-05-10T00:00:00Z");
        insertUsageRecordAt(adminId, projectId, "legal", 200, 80, "0.02000000", "2026-05-20T00:00:00Z");

        mockMvc.perform(get("/usage-records/by-date").cookie(adminCookie)
                        .param("to", "2026-05-15"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].date").value("2026-05-10"));
    }

    @Test
    void by_date_from_to가_같으면_당일_데이터만_조회된다() throws Exception {
        Map<String, String> admin = createUser("admin");
        Cookie adminCookie = loginCookie(admin);
        int adminId = readUserId(admin);
        int projectId = createProject(adminCookie);

        insertUsageRecordAt(adminId, projectId, "legal", 100, 50, "0.01000000", "2026-05-10T00:00:00Z");
        insertUsageRecordAt(adminId, projectId, "legal", 200, 80, "0.02000000", "2026-05-20T00:00:00Z");

        mockMvc.perform(get("/usage-records/by-date").cookie(adminCookie)
                        .param("from", "2026-05-10")
                        .param("to", "2026-05-10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].date").value("2026-05-10"));
    }

    @Test
    void by_date_user는_본인_데이터만_조회된다() throws Exception {
        Map<String, String> admin = createUser("admin");
        Map<String, String> user1 = createUser("user");
        Map<String, String> user2 = createUser("user");
        Cookie adminCookie = loginCookie(admin);
        Cookie user1Cookie = loginCookie(user1);
        int user1Id = readUserId(user1);
        int user2Id = readUserId(user2);
        int projectId = createProject(adminCookie);

        insertUsageRecordAt(user1Id, projectId, "legal", 100, 50, "0.01000000", "2026-05-10T00:00:00Z");
        insertUsageRecordAt(user2Id, projectId, "legal", 200, 80, "0.02000000", "2026-05-10T00:00:00Z");

        mockMvc.perform(get("/usage-records/by-date").cookie(user1Cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].inputTokens").value(100));
    }

    @Test
    void by_date_미인증_요청은_401을_반환한다() throws Exception {
        mockMvc.perform(get("/usage-records/by-date"))
                .andExpect(status().isUnauthorized());
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
