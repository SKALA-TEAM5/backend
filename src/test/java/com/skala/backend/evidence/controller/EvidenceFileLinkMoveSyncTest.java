package com.skala.backend.evidence.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skala.backend.user.domain.RoleCode;
import com.skala.backend.user.domain.User;
import com.skala.backend.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class EvidenceFileLinkMoveSyncTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired EntityManager entityManager;

    @Test
    void moveLink로_다른_타입으로_이동하면_files_uploaded_evidence_type_code도_동기화된다() throws Exception {
        Map<String, String> user = createUser("admin");
        Cookie cookie = loginCookie(user);
        int userId = getUserId(user.get("employeeNo"));
        int projectId = createProject(cookie);
        int statementId = insertStatement(projectId, "draft");
        int itemId1 = insertItem(statementId);
        int itemId2 = insertItem(statementId);
        int fileId = insertFile(projectId, userId, "receipt");
        int linkId = insertLink(itemId1, fileId, "receipt");

        mockMvc.perform(patch("/projects/{pid}/evidence-file-links/{lid}", projectId, linkId)
                        .cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("targetItemId", itemId2, "evidenceTypeCode", "site_photo"))))
                .andExpect(status().isOk());

        entityManager.flush();
        entityManager.clear();

        String linkType = jdbcTemplate.queryForObject(
                "SELECT evidence_type_code FROM service.evidence_file_links WHERE id = ?",
                String.class, linkId);
        String fileType = jdbcTemplate.queryForObject(
                "SELECT uploaded_evidence_type_code FROM service.files WHERE id = ?",
                String.class, fileId);

        assert "site_photo".equals(linkType) : "expected link site_photo but got " + linkType;
        assert "site_photo".equals(fileType) : "expected file site_photo but got " + fileType;
    }

    @Test
    void moveLink로_같은_타입_다른_항목으로_이동해도_files_uploaded_evidence_type_code는_유지된다() throws Exception {
        Map<String, String> user = createUser("admin");
        Cookie cookie = loginCookie(user);
        int userId = getUserId(user.get("employeeNo"));
        int projectId = createProject(cookie);
        int statementId = insertStatement(projectId, "draft");
        int itemId1 = insertItem(statementId);
        int itemId2 = insertItem(statementId);
        int fileId = insertFile(projectId, userId, "receipt");
        int linkId = insertLink(itemId1, fileId, "receipt");

        mockMvc.perform(patch("/projects/{pid}/evidence-file-links/{lid}", projectId, linkId)
                        .cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("targetItemId", itemId2, "evidenceTypeCode", "receipt"))))
                .andExpect(status().isOk());

        entityManager.flush();
        entityManager.clear();

        String fileType = jdbcTemplate.queryForObject(
                "SELECT uploaded_evidence_type_code FROM service.files WHERE id = ?",
                String.class, fileId);

        assert "receipt".equals(fileType) : "expected file receipt but got " + fileType;
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

    private int getUserId(String employeeNo) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM service.users WHERE employee_no = ?", Integer.class, employeeNo);
    }

    private int insertFile(int projectId, int uploadedByUserId, String uploadedEvidenceTypeCode) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO service.files
                    (project_id, uploaded_by_user_id, storage_key, original_filename, mime_type,
                     size_bytes, uploaded_evidence_type_code, status_code)
                VALUES (?, ?, ?, 'test.pdf', 'application/pdf', 1024, ?, 'success')
                RETURNING id
                """, Integer.class, projectId, uploadedByUserId, "test-key-" + UUID.randomUUID(),
                uploadedEvidenceTypeCode);
    }

    private int insertLink(int itemId, int fileId, String evidenceTypeCode) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO service.evidence_file_links
                    (usage_statement_item_id, file_id, evidence_type_code)
                VALUES (?, ?, ?)
                RETURNING id
                """, Integer.class, itemId, fileId, evidenceTypeCode);
    }
}
