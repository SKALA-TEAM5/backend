package com.skala.backend.project.controller;

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

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProjectAssigneesByRoleTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;

    @Test
    void 프로젝트_생성_응답에_생성자가_admin으로_분류된다() throws Exception {
        Map<String, String> admin = createUser("admin");
        Cookie adminCookie = loginCookie(admin);
        int adminId = getUserId(admin);

        mockMvc.perform(post("/projects")
                        .cookie(adminCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(projectRequest("생성_응답_역할분류"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.project.assigneesByRole.admin", hasSize(1)))
                .andExpect(jsonPath("$.data.project.assigneesByRole.admin[0].userId").value(adminId))
                .andExpect(jsonPath("$.data.project.assigneesByRole.user", hasSize(0)));
    }

    @Test
    void 프로젝트_상세_조회_시_admin과_user가_역할별로_분류된다() throws Exception {
        Map<String, String> admin = createUser("admin");
        Cookie adminCookie = loginCookie(admin);
        int adminId = getUserId(admin);
        int userId = createUserId("user");
        int projectId = createProject(adminCookie, "상세_역할분류");

        mockMvc.perform(post("/projects/{projectId}/assignees/{userId}", projectId, userId)
                        .cookie(adminCookie))
                .andExpect(status().isOk());

        mockMvc.perform(get("/projects/{projectId}", projectId).cookie(adminCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.project.assigneesByRole.admin", hasSize(1)))
                .andExpect(jsonPath("$.data.project.assigneesByRole.admin[0].userId").value(adminId))
                .andExpect(jsonPath("$.data.project.assigneesByRole.user", hasSize(1)))
                .andExpect(jsonPath("$.data.project.assigneesByRole.user[0].userId").value(userId));
    }

    @Test
    void 프로젝트_목록_조회_시_admin과_user가_역할별로_분류된다() throws Exception {
        Map<String, String> admin = createUser("admin");
        Cookie adminCookie = loginCookie(admin);
        int adminId = getUserId(admin);
        int userId = createUserId("user");
        String prefix = "목록_역할분류-" + UUID.randomUUID();
        int projectId = createProject(adminCookie, prefix);

        mockMvc.perform(post("/projects/{projectId}/assignees/{userId}", projectId, userId)
                        .cookie(adminCookie))
                .andExpect(status().isOk());

        mockMvc.perform(get("/projects").cookie(adminCookie).param("projectName", prefix))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].assigneesByRole.admin", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].assigneesByRole.admin[0].userId").value(adminId))
                .andExpect(jsonPath("$.data.items[0].assigneesByRole.user", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].assigneesByRole.user[0].userId").value(userId));
    }

    @Test
    void 프로젝트_수정_응답에도_assigneesByRole이_포함된다() throws Exception {
        Map<String, String> admin = createUser("admin");
        Cookie adminCookie = loginCookie(admin);
        int adminId = getUserId(admin);
        int userId = createUserId("user");
        int projectId = createProject(adminCookie, "수정_역할분류");

        mockMvc.perform(post("/projects/{projectId}/assignees/{userId}", projectId, userId)
                        .cookie(adminCookie))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/projects/{projectId}", projectId)
                        .cookie(adminCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("projectName", "수정_역할분류_수정됨"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.project.assigneesByRole.admin", hasSize(1)))
                .andExpect(jsonPath("$.data.project.assigneesByRole.admin[0].userId").value(adminId))
                .andExpect(jsonPath("$.data.project.assigneesByRole.user", hasSize(1)))
                .andExpect(jsonPath("$.data.project.assigneesByRole.user[0].userId").value(userId));
    }

    @Test
    void agent_역할은_assigneesByRole에_포함되지_않는다() throws Exception {
        Cookie adminCookie = loginCookie(createUser("admin"));
        Cookie agentCookie = loginCookie(createUser("agent"));
        int agentId = createUserId("agent");
        int projectId = createProject(adminCookie, "agent_역할_제외");

        mockMvc.perform(post("/projects/{projectId}/assignees/{userId}", projectId, agentId)
                        .cookie(agentCookie))
                .andExpect(status().isOk());

        mockMvc.perform(get("/projects/{projectId}", projectId).cookie(adminCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.project.assigneesByRole.admin", hasSize(1)))
                .andExpect(jsonPath("$.data.project.assigneesByRole.user", hasSize(0)));
    }

    @Test
    void 여러_admin과_user가_있을_때_모두_assigneesByRole에_포함된다() throws Exception {
        Cookie adminCookie = loginCookie(createUser("admin"));
        Cookie agentCookie = loginCookie(createUser("agent"));
        int secondAdminId = createUserId("admin");
        int firstUserId = createUserId("user");
        int secondUserId = createUserId("user");
        int projectId = createProject(adminCookie, "다중담당자_역할분류");

        mockMvc.perform(post("/projects/{projectId}/assignees/{userId}", projectId, secondAdminId)
                        .cookie(agentCookie))
                .andExpect(status().isOk());
        mockMvc.perform(post("/projects/{projectId}/assignees/{userId}", projectId, firstUserId)
                        .cookie(adminCookie))
                .andExpect(status().isOk());
        mockMvc.perform(post("/projects/{projectId}/assignees/{userId}", projectId, secondUserId)
                        .cookie(adminCookie))
                .andExpect(status().isOk());

        mockMvc.perform(get("/projects/{projectId}", projectId).cookie(adminCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.project.assigneesByRole.admin", hasSize(2)))
                .andExpect(jsonPath("$.data.project.assigneesByRole.user", hasSize(2)))
                .andExpect(jsonPath("$.data.project.assigneesByRole.user[*].userId",
                        containsInAnyOrder(firstUserId, secondUserId)));
    }

    @Test
    void 여러_admin과_user가_있을_때_목록에서도_모두_assigneesByRole에_포함된다() throws Exception {
        Cookie adminCookie = loginCookie(createUser("admin"));
        Cookie agentCookie = loginCookie(createUser("agent"));
        int secondAdminId = createUserId("admin");
        int firstUserId = createUserId("user");
        int secondUserId = createUserId("user");
        String prefix = "다중담당자_목록-" + UUID.randomUUID();
        int projectId = createProject(adminCookie, prefix);

        mockMvc.perform(post("/projects/{projectId}/assignees/{userId}", projectId, secondAdminId)
                        .cookie(agentCookie))
                .andExpect(status().isOk());
        mockMvc.perform(post("/projects/{projectId}/assignees/{userId}", projectId, firstUserId)
                        .cookie(adminCookie))
                .andExpect(status().isOk());
        mockMvc.perform(post("/projects/{projectId}/assignees/{userId}", projectId, secondUserId)
                        .cookie(adminCookie))
                .andExpect(status().isOk());

        mockMvc.perform(get("/projects").cookie(adminCookie).param("projectName", prefix))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].assigneesByRole.admin", hasSize(2)))
                .andExpect(jsonPath("$.data.items[0].assigneesByRole.user", hasSize(2)))
                .andExpect(jsonPath("$.data.items[0].assigneesByRole.user[*].userId",
                        containsInAnyOrder(firstUserId, secondUserId)));
    }

    @Test
    void assigneesByRole에는_userId와_realName이_포함된다() throws Exception {
        Map<String, String> admin = createUser("admin", "관리자홍길동");
        Cookie adminCookie = loginCookie(admin);
        int adminId = getUserId(admin);
        String prefix = "필드검증-" + UUID.randomUUID();
        int projectId = createProject(adminCookie, prefix);

        mockMvc.perform(get("/projects/{projectId}", projectId).cookie(adminCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.project.assigneesByRole.admin[0].userId").value(adminId))
                .andExpect(jsonPath("$.data.project.assigneesByRole.admin[0].realName").value("관리자홍길동"));

        mockMvc.perform(get("/projects").cookie(adminCookie).param("projectName", prefix))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].assigneesByRole.admin[0].userId").value(adminId))
                .andExpect(jsonPath("$.data.items[0].assigneesByRole.admin[0].realName").value("관리자홍길동"));
    }

    @Test
    void 담당자_교체_후_assigneesByRole이_갱신된다() throws Exception {
        Cookie adminCookie = loginCookie(createUser("admin"));
        Cookie agentCookie = loginCookie(createUser("agent"));
        int firstUserId = createUserId("user");
        int replacementUserId = createUserId("user");
        int projectId = createProject(adminCookie, "교체_후_역할분류");

        mockMvc.perform(put("/projects/{projectId}/assignees", projectId)
                        .cookie(adminCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("assigneeUserIds", List.of(firstUserId)))))
                .andExpect(status().isOk());

        mockMvc.perform(put("/projects/{projectId}/assignees", projectId)
                        .cookie(adminCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("assigneeUserIds", List.of(replacementUserId)))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/projects/{projectId}", projectId).cookie(adminCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.project.assigneesByRole.user", hasSize(1)))
                .andExpect(jsonPath("$.data.project.assigneesByRole.user[0].userId").value(replacementUserId));
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private Map<String, String> createUser(String roleCode) {
        return createUser(roleCode, "홍길동");
    }

    private Map<String, String> createUser(String roleCode, String realName) {
        Map<String, String> req = Map.of(
                "employeeNo", "EMP-" + UUID.randomUUID(),
                "realName", realName,
                "password", "P@ssw0rd123!",
                "roleCode", roleCode
        );
        userRepository.saveAndFlush(User.create(
                req.get("employeeNo"),
                req.get("realName"),
                passwordEncoder.encode(req.get("password")),
                RoleCode.from(roleCode)
        ));
        return req;
    }

    private Cookie loginCookie(Map<String, String> user) throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "employeeNo", user.get("employeeNo"),
                                "password", user.get("password")
                        ))))
                .andExpect(status().isOk())
                .andReturn();
        return result.getResponse().getCookie("access_token");
    }

    private int getUserId(Map<String, String> user) throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "employeeNo", user.get("employeeNo"),
                                "password", user.get("password")
                        ))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("user").path("id").asInt();
    }

    private int createUserId(String roleCode) throws Exception {
        return getUserId(createUser(roleCode));
    }

    private int createProject(Cookie adminCookie, String projectName) throws Exception {
        MvcResult result = mockMvc.perform(post("/projects")
                        .cookie(adminCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(projectRequest(projectName))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("project").path("id").asInt();
    }

    private Map<String, Object> projectRequest(String projectName) {
        return Map.of(
                "contractNo", "CN-" + UUID.randomUUID(),
                "constructionCompany", "스칼라건설",
                "projectName", projectName,
                "siteLocation", "서울시 강남구",
                "contractAmount", 100000000,
                "constructionStartDate", "2026-05-01",
                "constructionEndDate", "2026-12-31",
                "appropriatedAmount", 10000000,
                "status", "active"
        );
    }
}
