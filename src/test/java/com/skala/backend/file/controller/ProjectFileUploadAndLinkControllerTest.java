package com.skala.backend.file.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skala.backend.user.domain.RoleCode;
import com.skala.backend.user.domain.User;
import com.skala.backend.user.repository.UserRepository;
import io.minio.MinioClient;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProjectFileUploadAndLinkControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired EntityManager entityManager;

    @MockitoBean
    MinioClient minioClient;

    // ─── 정상 케이스 ──────────────────────────────────────────────────────

    @Test
    void 업로드_후_즉시_연결_성공_시_fileId와_linkId를_반환한다() throws Exception {
        Cookie cookie = loginCookie(createUser("admin"));
        int projectId = createProject(cookie);
        int statementId = insertStatement(projectId, "draft");
        int itemId = insertItem(statementId);
        MockMultipartFile file = new MockMultipartFile("file", "receipt.jpg", "image/jpeg", "data".getBytes());

        mockMvc.perform(multipart("/projects/{pid}/usage-statement-items/{iid}/evidence-files/upload", projectId, itemId)
                        .file(file)
                        .param("evidenceTypeCode", "receipt")
                        .cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.fileId").isNumber())
                .andExpect(jsonPath("$.data.linkId").isNumber());
    }

    @Test
    void 업로드_후_즉시_연결_시_files_테이블과_evidence_file_links_테이블에_모두_레코드가_생긴다() throws Exception {
        Cookie cookie = loginCookie(createUser("admin"));
        int projectId = createProject(cookie);
        int statementId = insertStatement(projectId, "draft");
        int itemId = insertItem(statementId);
        MockMultipartFile file = new MockMultipartFile("file", "tax.pdf", "application/pdf", "pdf-data".getBytes());

        MvcResult result = mockMvc.perform(multipart("/projects/{pid}/usage-statement-items/{iid}/evidence-files/upload", projectId, itemId)
                        .file(file)
                        .param("evidenceTypeCode", "tax_invoice")
                        .cookie(cookie))
                .andExpect(status().isOk())
                .andReturn();

        entityManager.flush();
        entityManager.clear();

        long fileId = objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("fileId").asLong();
        long linkId = objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("linkId").asLong();

        Integer fileCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM service.files WHERE id = ? AND deleted_at IS NULL", Integer.class, fileId);
        Integer linkCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM service.evidence_file_links WHERE id = ? AND usage_statement_item_id = ?",
                Integer.class, linkId, itemId);

        assert fileCount != null && fileCount == 1 : "files 레코드가 없습니다";
        assert linkCount != null && linkCount == 1 : "evidence_file_links 레코드가 없습니다";
    }

    @Test
    void supplement_required_상태에서_업로드_연결_시_draft로_전환된다() throws Exception {
        Cookie cookie = loginCookie(createUser("admin"));
        int projectId = createProject(cookie);
        int statementId = insertStatement(projectId, "supplement_required");
        int itemId = insertItem(statementId);
        MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", "img".getBytes());

        mockMvc.perform(multipart("/projects/{pid}/usage-statement-items/{iid}/evidence-files/upload", projectId, itemId)
                        .file(file)
                        .param("evidenceTypeCode", "site_photo")
                        .cookie(cookie))
                .andExpect(status().isOk());

        entityManager.flush();
        entityManager.clear();

        String statusCode = jdbcTemplate.queryForObject(
                "SELECT status_code FROM service.usage_statements WHERE id = ?", String.class, statementId);
        assert "draft".equals(statusCode) : "expected draft but got " + statusCode;
    }

    @Test
    void upload_completed_상태에서_업로드_연결_해도_상태_변화_없다() throws Exception {
        Cookie cookie = loginCookie(createUser("admin"));
        int projectId = createProject(cookie);
        int statementId = insertStatement(projectId, "upload_completed");
        int itemId = insertItem(statementId);
        MockMultipartFile file = new MockMultipartFile("file", "stub.jpg", "image/jpeg", "img".getBytes());

        mockMvc.perform(multipart("/projects/{pid}/usage-statement-items/{iid}/evidence-files/upload", projectId, itemId)
                        .file(file)
                        .param("evidenceTypeCode", "pay_stub")
                        .cookie(cookie))
                .andExpect(status().isOk());

        entityManager.flush();
        entityManager.clear();

        String statusCode = jdbcTemplate.queryForObject(
                "SELECT status_code FROM service.usage_statements WHERE id = ?", String.class, statementId);
        assert "upload_completed".equals(statusCode) : "expected upload_completed but got " + statusCode;
    }

    // ─── 오류 케이스 ──────────────────────────────────────────────────────

    @Test
    void 존재하지_않는_항목에_업로드_연결_시_404() throws Exception {
        Cookie cookie = loginCookie(createUser("admin"));
        int projectId = createProject(cookie);
        long nonExistentItemId = 999_999_999L;
        MockMultipartFile file = new MockMultipartFile("file", "doc.pdf", "application/pdf", "data".getBytes());

        mockMvc.perform(multipart("/projects/{pid}/usage-statement-items/{iid}/evidence-files/upload", projectId, nonExistentItemId)
                        .file(file)
                        .param("evidenceTypeCode", "receipt")
                        .cookie(cookie))
                .andExpect(status().isNotFound());
    }

    @Test
    void 잘못된_증빙_타입으로_업로드_연결_시_400() throws Exception {
        Cookie cookie = loginCookie(createUser("admin"));
        int projectId = createProject(cookie);
        int statementId = insertStatement(projectId, "draft");
        int itemId = insertItem(statementId);
        MockMultipartFile file = new MockMultipartFile("file", "doc.pdf", "application/pdf", "data".getBytes());

        mockMvc.perform(multipart("/projects/{pid}/usage-statement-items/{iid}/evidence-files/upload", projectId, itemId)
                        .file(file)
                        .param("evidenceTypeCode", "invalid_type")
                        .cookie(cookie))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 파일_없이_요청하면_400() throws Exception {
        Cookie cookie = loginCookie(createUser("admin"));
        int projectId = createProject(cookie);
        int statementId = insertStatement(projectId, "draft");
        int itemId = insertItem(statementId);

        mockMvc.perform(multipart("/projects/{pid}/usage-statement-items/{iid}/evidence-files/upload", projectId, itemId)
                        .param("evidenceTypeCode", "receipt")
                        .cookie(cookie))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 다른_프로젝트의_항목에_연결_시_404() throws Exception {
        Cookie cookie = loginCookie(createUser("admin"));
        int projectId1 = createProject(cookie);
        int projectId2 = createProject(cookie);
        int statementId = insertStatement(projectId2, "draft");
        int itemId = insertItem(statementId);
        MockMultipartFile file = new MockMultipartFile("file", "doc.jpg", "image/jpeg", "data".getBytes());

        // projectId1으로 업로드하지만 itemId는 projectId2 소속
        mockMvc.perform(multipart("/projects/{pid}/usage-statement-items/{iid}/evidence-files/upload", projectId1, itemId)
                        .file(file)
                        .param("evidenceTypeCode", "receipt")
                        .cookie(cookie))
                .andExpect(status().isNotFound());
    }

    @Test
    void 인증_없이_요청하면_401() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "doc.jpg", "image/jpeg", "data".getBytes());

        mockMvc.perform(multipart("/projects/{pid}/usage-statement-items/{iid}/evidence-files/upload", 1L, 1L)
                        .file(file)
                        .param("evidenceTypeCode", "receipt"))
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

    private int insertStatement(int projectId, String statusCode) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO service.usage_statements
                    (project_id, report_month, revision_no, document_written_date, cumulative_progress_rate, status_code)
                VALUES (?, '2026-05-01', 1, '2026-05-01', 30, ?)
                RETURNING id
                """, Integer.class, projectId, statusCode);
    }

    private int insertItem(int statementId) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO service.usage_statement_items
                    (usage_statement_id, category_code, used_on, item_name, unit, quantity, unit_price, total_amount, page_no)
                VALUES (?, 'CAT_01', '2026-05-01', '안전관리자 임금', '월', 1, 500000, 500000, 1)
                RETURNING id
                """, Integer.class, statementId);
    }
}
