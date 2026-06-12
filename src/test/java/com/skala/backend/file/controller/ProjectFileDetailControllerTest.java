package com.skala.backend.file.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skala.backend.user.domain.RoleCode;
import com.skala.backend.user.domain.User;
import com.skala.backend.user.repository.UserRepository;
import io.minio.MinioClient;
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

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 파일 업로드/목록 응답에 visionDetections가 노출되지 않음을 검증한다.
 * vision_validation 파싱 동작 자체는 세부내역 파일보기(evidence-files) 응답에서만 노출되며
 * {@code EvidenceFileVisionDetectionsControllerTest}가 커버한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProjectFileDetailControllerTest {

    static final String VISION_DETAIL = """
            {"vision_validation": {
              "reason": "안전모가 1건 확인되었습니다.",
              "detections": [{
                "label": "안전모 착용",
                "bbox_xyxy": [241.49, 33.61, 431.7, 278.23],
                "box_color": "blue",
                "confidence": 0.8332,
                "is_wearing": true
              }],
              "image_width": 677,
              "image_height": 493
            }}
            """;

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;

    @MockitoBean
    MinioClient minioClient;

    record Session(Cookie cookie, int userId) {}

    @Test
    void 업로드_응답에는_visionDetections_필드가_없다() throws Exception {
        Session s = login(createUser("admin"));
        int projectId = createProject(s.cookie());
        MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", "data".getBytes());

        mockMvc.perform(multipart("/projects/{pid}/files", projectId)
                        .file(file)
                        .param("evidenceTypeCode", "site_photo")
                        .cookie(s.cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.visionDetections").doesNotExist());
    }

    @Test
    void 파일_목록_응답에는_vision_validation이_있어도_visionDetections가_노출되지_않는다() throws Exception {
        Session s = login(createUser("admin"));
        int projectId = createProject(s.cookie());
        insertFile(s.userId(), projectId, "site_photo", VISION_DETAIL);

        mockMvc.perform(get("/projects/{pid}/files", projectId).cookie(s.cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].visionDetections").doesNotExist());
    }

    // ─── fixtures ─────────────────────────────────────────────────────────

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

    private void insertFile(int userId, int projectId, String evidenceTypeCode, String detailJson) {
        jdbcTemplate.update("""
                INSERT INTO service.files
                    (project_id, uploaded_by_user_id, uploaded_evidence_type_code,
                     original_filename, storage_key, mime_type, size_bytes, detail)
                VALUES (?, ?, ?, 'photo.jpg', ?, 'image/jpeg', 1024, ?::jsonb)
                """, projectId, userId, evidenceTypeCode, "key-" + UUID.randomUUID(), detailJson);
    }
}
