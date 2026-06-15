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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProjectFileRenameControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired EntityManager entityManager;

    @MockitoBean
    MinioClient minioClient;

    // ─── 정상 케이스 ─────────────────────────────────────────────────────────

    @Test
    void 파일명_수정_시_DB에_반영된다() throws Exception {
        Session s = login(createUser("admin"));
        int projectId = createProject(s.cookie());
        int fileId = insertFile(projectId, s.userId());

        mockMvc.perform(patch("/projects/{pid}/files/{fid}", projectId, fileId)
                        .cookie(s.cookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("originalFilename", "새파일명.pdf"))))
                .andExpect(status().isOk());

        entityManager.flush();
        entityManager.clear();
        String filename = jdbcTemplate.queryForObject(
                "SELECT original_filename FROM service.files WHERE id = ?", String.class, fileId);
        assertThat(filename).isEqualTo("새파일명.pdf");
    }

    @Test
    void 파일명_수정_시_연결된_사용내역서가_draft로_전환된다() throws Exception {
        Session s = login(createUser("admin"));
        int projectId = createProject(s.cookie());
        int statementId = insertStatement(projectId, "upload_completed");
        int itemId = insertItem(statementId);
        int fileId = insertFile(projectId, s.userId());
        insertLink(itemId, fileId, "receipt");

        mockMvc.perform(patch("/projects/{pid}/files/{fid}", projectId, fileId)
                        .cookie(s.cookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("originalFilename", "변경.pdf"))))
                .andExpect(status().isOk());

        entityManager.flush();
        entityManager.clear();
        String statusCode = jdbcTemplate.queryForObject(
                "SELECT status_code FROM service.usage_statements WHERE id = ?", String.class, statementId);
        assertThat(statusCode).isEqualTo("draft");
    }

    @Test
    void supplement_required_상태의_사용내역서도_draft로_전환된다() throws Exception {
        Session s = login(createUser("admin"));
        int projectId = createProject(s.cookie());
        int statementId = insertStatement(projectId, "supplement_required");
        int itemId = insertItem(statementId);
        int fileId = insertFile(projectId, s.userId());
        insertLink(itemId, fileId, "receipt");

        mockMvc.perform(patch("/projects/{pid}/files/{fid}", projectId, fileId)
                        .cookie(s.cookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("originalFilename", "변경.pdf"))))
                .andExpect(status().isOk());

        entityManager.flush();
        entityManager.clear();
        String statusCode = jdbcTemplate.queryForObject(
                "SELECT status_code FROM service.usage_statements WHERE id = ?", String.class, statementId);
        assertThat(statusCode).isEqualTo("draft");
    }

    @Test
    void 연결이_없는_파일_수정_시_사용내역서_상태는_유지된다() throws Exception {
        Session s = login(createUser("admin"));
        int projectId = createProject(s.cookie());
        int statementId = insertStatement(projectId, "upload_completed");
        int fileId = insertFile(projectId, s.userId());

        mockMvc.perform(patch("/projects/{pid}/files/{fid}", projectId, fileId)
                        .cookie(s.cookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("originalFilename", "변경.pdf"))))
                .andExpect(status().isOk());

        entityManager.flush();
        entityManager.clear();
        String statusCode = jdbcTemplate.queryForObject(
                "SELECT status_code FROM service.usage_statements WHERE id = ?", String.class, statementId);
        assertThat(statusCode).isEqualTo("upload_completed");
    }

    // ─── 유효성 검사 ─────────────────────────────────────────────────────────

    @Test
    void 파일명이_빈_문자열이면_400을_반환한다() throws Exception {
        Session s = login(createUser("admin"));
        int projectId = createProject(s.cookie());
        int fileId = insertFile(projectId, s.userId());

        mockMvc.perform(patch("/projects/{pid}/files/{fid}", projectId, fileId)
                        .cookie(s.cookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("originalFilename", ""))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 파일명이_공백만_있으면_400을_반환한다() throws Exception {
        Session s = login(createUser("admin"));
        int projectId = createProject(s.cookie());
        int fileId = insertFile(projectId, s.userId());

        mockMvc.perform(patch("/projects/{pid}/files/{fid}", projectId, fileId)
                        .cookie(s.cookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("originalFilename", "   "))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 파일명이_500자를_초과하면_400을_반환한다() throws Exception {
        Session s = login(createUser("admin"));
        int projectId = createProject(s.cookie());
        int fileId = insertFile(projectId, s.userId());
        String longName = "a".repeat(501);

        mockMvc.perform(patch("/projects/{pid}/files/{fid}", projectId, fileId)
                        .cookie(s.cookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("originalFilename", longName))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 파일명이_정확히_500자이면_성공한다() throws Exception {
        Session s = login(createUser("admin"));
        int projectId = createProject(s.cookie());
        int fileId = insertFile(projectId, s.userId());
        String maxName = "a".repeat(500);

        mockMvc.perform(patch("/projects/{pid}/files/{fid}", projectId, fileId)
                        .cookie(s.cookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("originalFilename", maxName))))
                .andExpect(status().isOk());
    }

    // ─── 오류 케이스 ──────────────────────────────────────────────────────────

    @Test
    void 존재하지_않는_파일_수정_시_404를_반환한다() throws Exception {
        Session s = login(createUser("admin"));
        int projectId = createProject(s.cookie());

        mockMvc.perform(patch("/projects/{pid}/files/{fid}", projectId, 99999)
                        .cookie(s.cookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("originalFilename", "변경.pdf"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void 다른_프로젝트의_파일은_수정할_수_없다() throws Exception {
        Session s = login(createUser("admin"));
        int projectA = createProject(s.cookie());
        int projectB = createProject(s.cookie());
        int fileId = insertFile(projectA, s.userId());

        mockMvc.perform(patch("/projects/{pid}/files/{fid}", projectB, fileId)
                        .cookie(s.cookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("originalFilename", "변경.pdf"))))
                .andExpect(status().isNotFound());

        entityManager.flush();
        entityManager.clear();
        String filename = jdbcTemplate.queryForObject(
                "SELECT original_filename FROM service.files WHERE id = ?", String.class, fileId);
        assertThat(filename).isEqualTo("test.pdf");
    }

    @Test
    void 소속되지_않은_admin은_파일을_수정할_수_없다() throws Exception {
        Session owner = login(createUser("admin"));
        Session other = login(createUser("admin"));
        int projectId = createProject(owner.cookie());
        int fileId = insertFile(projectId, owner.userId());

        mockMvc.perform(patch("/projects/{pid}/files/{fid}", projectId, fileId)
                        .cookie(other.cookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("originalFilename", "변경.pdf"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void 소속되지_않은_user는_파일을_수정할_수_없다() throws Exception {
        Session owner = login(createUser("admin"));
        Session other = login(createUser("user"));
        int projectId = createProject(owner.cookie());
        int fileId = insertFile(projectId, owner.userId());

        mockMvc.perform(patch("/projects/{pid}/files/{fid}", projectId, fileId)
                        .cookie(other.cookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("originalFilename", "변경.pdf"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void 소속된_user는_파일을_수정할_수_있다() throws Exception {
        Session owner = login(createUser("admin"));
        Session member = login(createUser("user"));
        int projectId = createProject(owner.cookie());
        assignUser(projectId, member.userId(), owner.cookie());
        int fileId = insertFile(projectId, owner.userId());

        mockMvc.perform(patch("/projects/{pid}/files/{fid}", projectId, fileId)
                        .cookie(member.cookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("originalFilename", "변경.pdf"))))
                .andExpect(status().isOk());
    }

    // ─── fixtures ─────────────────────────────────────────────────────────────

    record Session(Cookie cookie, int userId) {}

    private Map<String, String> createUser(String roleCode) {
        String empNo = "EMP-" + UUID.randomUUID();
        userRepository.saveAndFlush(User.create(
                empNo, "홍길동",
                passwordEncoder.encode("P@ssw0rd123!"),
                RoleCode.from(roleCode)
        ));
        return Map.of("employeeNo", empNo, "password", "P@ssw0rd123!");
    }

    private Session login(Map<String, String> credentials) throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "employeeNo", credentials.get("employeeNo"),
                                "password", credentials.get("password")
                        ))))
                .andExpect(status().isOk())
                .andReturn();
        Cookie cookie = result.getResponse().getCookie("access_token");
        int userId = objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("user").path("id").asInt();
        return new Session(cookie, userId);
    }

    private int createProject(Cookie cookie) throws Exception {
        MvcResult result = mockMvc.perform(post("/projects")
                        .cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "contractNo", "CN-" + UUID.randomUUID(),
                                "constructionCompany", "스칼라건설",
                                "projectName", "테스트-" + UUID.randomUUID(),
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

    private int insertFile(int projectId, int uploadedByUserId) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO service.files
                    (project_id, uploaded_by_user_id, storage_key, original_filename,
                     mime_type, size_bytes, uploaded_evidence_type_code, status_code)
                VALUES (?, ?, ?, 'test.pdf', 'application/pdf', 1024, 'receipt', 'success')
                RETURNING id
                """, Integer.class, projectId, uploadedByUserId, "test-key-" + UUID.randomUUID());
    }

    private int insertStatement(int projectId, String statusCode) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO service.usage_statements
                    (project_id, report_month, revision_no, document_written_date,
                     cumulative_progress_rate, status_code)
                VALUES (?, '2026-05-01', 1, '2026-05-01', 30, ?)
                RETURNING id
                """, Integer.class, projectId, statusCode);
    }

    private int insertItem(int statementId) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO service.usage_statement_items
                    (usage_statement_id, category_code, used_on, item_name, unit,
                     quantity, unit_price, total_amount, page_no)
                VALUES (?, 'CAT_01', '2026-05-01', '안전관리자 임금', '월', 1, 500000, 500000, 1)
                RETURNING id
                """, Integer.class, statementId);
    }

    private void assignUser(int projectId, int userId, Cookie cookie) throws Exception {
        mockMvc.perform(post("/projects/{pid}/assignees/{uid}", projectId, userId)
                        .cookie(cookie))
                .andExpect(status().isOk());
    }

    private void insertLink(int itemId, int fileId, String evidenceTypeCode) {
        jdbcTemplate.update("""
                INSERT INTO service.evidence_file_links
                    (usage_statement_item_id, file_id, evidence_type_code)
                VALUES (?, ?, ?)
                """, itemId, fileId, evidenceTypeCode);
    }
}
