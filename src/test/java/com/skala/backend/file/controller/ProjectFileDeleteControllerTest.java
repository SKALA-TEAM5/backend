package com.skala.backend.file.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skala.backend.user.domain.RoleCode;
import com.skala.backend.user.domain.User;
import com.skala.backend.user.repository.UserRepository;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProjectFileDeleteControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired EntityManager entityManager;

    @MockitoBean
    MinioClient minioClient;

    // ─── DB 하드 딜리트 ───────────────────────────────────────────────────

    @Test
    void 파일_삭제_시_files_테이블에서_레코드가_삭제된다() throws Exception {
        Session s = login(createUser("admin"));
        int projectId = createProject(s.cookie());
        int fileId = insertFile(projectId, s.userId());

        mockMvc.perform(delete("/projects/{pid}/files/{fid}", projectId, fileId)
                        .cookie(s.cookie()))
                .andExpect(status().isOk());

        entityManager.flush();
        entityManager.clear();
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM service.files WHERE id = ?", Integer.class, fileId);
        assertThat(count).isZero();
    }

    @Test
    void 파일_삭제_시_MinIO_removeObject가_호출된다() throws Exception {
        Session s = login(createUser("admin"));
        int projectId = createProject(s.cookie());
        int fileId = insertFile(projectId, s.userId());

        mockMvc.perform(delete("/projects/{pid}/files/{fid}", projectId, fileId)
                        .cookie(s.cookie()))
                .andExpect(status().isOk());

        verify(minioClient).removeObject(any(RemoveObjectArgs.class));
    }

    // ─── evidence_file_links 연쇄 삭제 ───────────────────────────────────

    @Test
    void 파일_삭제_시_연결된_evidence_file_links가_삭제된다() throws Exception {
        Session s = login(createUser("admin"));
        int projectId = createProject(s.cookie());
        int statementId = insertStatement(projectId, "draft");
        int itemId = insertItem(statementId);
        int fileId = insertFile(projectId, s.userId());
        insertLink(itemId, fileId, "receipt");

        mockMvc.perform(delete("/projects/{pid}/files/{fid}", projectId, fileId)
                        .cookie(s.cookie()))
                .andExpect(status().isOk());

        entityManager.flush();
        entityManager.clear();
        Integer linkCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM service.evidence_file_links WHERE file_id = ?",
                Integer.class, fileId);
        assertThat(linkCount).isZero();
    }

    @Test
    void 파일_삭제_시_파일이_없던_항목은_links_영향_없음() throws Exception {
        Session s = login(createUser("admin"));
        int projectId = createProject(s.cookie());
        int statementId = insertStatement(projectId, "draft");
        int itemId = insertItem(statementId);
        int otherFileId = insertFile(projectId, s.userId());
        insertLink(itemId, otherFileId, "receipt");
        int fileId = insertFile(projectId, s.userId());

        mockMvc.perform(delete("/projects/{pid}/files/{fid}", projectId, fileId)
                        .cookie(s.cookie()))
                .andExpect(status().isOk());

        entityManager.flush();
        entityManager.clear();
        Integer otherLinkCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM service.evidence_file_links WHERE file_id = ?",
                Integer.class, otherFileId);
        assertThat(otherLinkCount).isOne();
    }

    // ─── source_file_id null 처리 ─────────────────────────────────────────

    @Test
    void 파일_삭제_시_usage_statement_source_file_id가_null로_설정된다() throws Exception {
        Session s = login(createUser("admin"));
        int projectId = createProject(s.cookie());
        int fileId = insertFile(projectId, s.userId());
        int statementId = insertStatementWithSourceFile(projectId, fileId);

        mockMvc.perform(delete("/projects/{pid}/files/{fid}", projectId, fileId)
                        .cookie(s.cookie()))
                .andExpect(status().isOk());

        entityManager.flush();
        entityManager.clear();
        Integer sourceFileId = jdbcTemplate.queryForObject(
                "SELECT source_file_id FROM service.usage_statements WHERE id = ?",
                Integer.class, statementId);
        assertThat(sourceFileId).isNull();
    }

    @Test
    void source_file_id가_없는_statement는_영향_없음() throws Exception {
        Session s = login(createUser("admin"));
        int projectId = createProject(s.cookie());
        int fileId = insertFile(projectId, s.userId());
        int statementId = insertStatement(projectId, "draft");

        mockMvc.perform(delete("/projects/{pid}/files/{fid}", projectId, fileId)
                        .cookie(s.cookie()))
                .andExpect(status().isOk());

        entityManager.flush();
        entityManager.clear();
        Integer sourceFileId = jdbcTemplate.queryForObject(
                "SELECT source_file_id FROM service.usage_statements WHERE id = ?",
                Integer.class, statementId);
        assertThat(sourceFileId).isNull();
    }

    // ─── supplement_required → draft 자동 전환 ───────────────────────────

    @Test
    void 파일_삭제_시_연결된_항목의_statement가_supplement_required이면_draft로_전환된다() throws Exception {
        Session s = login(createUser("admin"));
        int projectId = createProject(s.cookie());
        int statementId = insertStatement(projectId, "supplement_required");
        int itemId = insertItem(statementId);
        int fileId = insertFile(projectId, s.userId());
        insertLink(itemId, fileId, "receipt");

        mockMvc.perform(delete("/projects/{pid}/files/{fid}", projectId, fileId)
                        .cookie(s.cookie()))
                .andExpect(status().isOk());

        entityManager.flush();
        entityManager.clear();
        String statusCode = jdbcTemplate.queryForObject(
                "SELECT status_code FROM service.usage_statements WHERE id = ?",
                String.class, statementId);
        assertThat(statusCode).isEqualTo("draft");
    }

    @Test
    void 파일_삭제_시_연결이_없으면_statement_상태는_supplement_required_유지된다() throws Exception {
        Session s = login(createUser("admin"));
        int projectId = createProject(s.cookie());
        int statementId = insertStatement(projectId, "supplement_required");
        int fileId = insertFile(projectId, s.userId());

        mockMvc.perform(delete("/projects/{pid}/files/{fid}", projectId, fileId)
                        .cookie(s.cookie()))
                .andExpect(status().isOk());

        entityManager.flush();
        entityManager.clear();
        String statusCode = jdbcTemplate.queryForObject(
                "SELECT status_code FROM service.usage_statements WHERE id = ?",
                String.class, statementId);
        assertThat(statusCode).isEqualTo("supplement_required");
    }

    // ─── 오류 케이스 ──────────────────────────────────────────────────────

    @Test
    void 존재하지_않는_파일_삭제_시_404를_반환한다() throws Exception {
        Session s = login(createUser("admin"));
        int projectId = createProject(s.cookie());

        mockMvc.perform(delete("/projects/{pid}/files/{fid}", projectId, 99999)
                        .cookie(s.cookie()))
                .andExpect(status().isNotFound());
    }

    @Test
    void 다른_프로젝트의_파일은_삭제할_수_없다() throws Exception {
        Session s = login(createUser("admin"));
        int projectA = createProject(s.cookie());
        int projectB = createProject(s.cookie());
        int fileId = insertFile(projectA, s.userId());

        mockMvc.perform(delete("/projects/{pid}/files/{fid}", projectB, fileId)
                        .cookie(s.cookie()))
                .andExpect(status().isNotFound());

        entityManager.flush();
        entityManager.clear();
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM service.files WHERE id = ?", Integer.class, fileId);
        assertThat(count).isOne();
    }

    // ─── fixtures ─────────────────────────────────────────────────────────

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

    private int insertStatementWithSourceFile(int projectId, int sourceFileId) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO service.usage_statements
                    (project_id, report_month, revision_no, document_written_date,
                     cumulative_progress_rate, status_code, source_file_id)
                VALUES (?, '2026-05-01', 1, '2026-05-01', 30, 'draft', ?)
                RETURNING id
                """, Integer.class, projectId, sourceFileId);
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

    private void insertLink(int itemId, int fileId, String evidenceTypeCode) {
        jdbcTemplate.update("""
                INSERT INTO service.evidence_file_links
                    (usage_statement_item_id, file_id, evidence_type_code)
                VALUES (?, ?, ?)
                """, itemId, fileId, evidenceTypeCode);
    }
}
