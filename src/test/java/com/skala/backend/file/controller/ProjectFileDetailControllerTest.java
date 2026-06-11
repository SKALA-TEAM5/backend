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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProjectFileDetailControllerTest {

    static final String VISION_DETAIL = """
            {"vision_validation": {
              "reason": "안전모가 1건 확인되었습니다.",
              "detections": [{
                "label": "안전모 착용",
                "class_id": 3,
                "bbox_xyxy": [241.49, 33.61, 431.7, 278.23],
                "box_color": "blue",
                "equipment": "safety_helmet",
                "class_code": "07",
                "confidence": 0.8332,
                "is_wearing": true,
                "needs_review": false
              }],
              "image_width": 677,
              "result_code": "hil",
              "status_code": "success",
              "image_height": 493,
              "is_appropriate": true,
              "original_filename": "스크린샷 2026-06-11 10.11.02.png",
              "usage_statement_id": 2
            }}
            """;

    static final String MULTI_DETECTION_DETAIL = """
            {"vision_validation": {
              "reason": "안전모 2건, 안전벨트 1건 확인.",
              "detections": [
                {"label": "안전모 착용", "bbox_xyxy": [10.0, 20.0, 30.0, 40.0],
                 "box_color": "blue", "confidence": 0.91, "is_wearing": true},
                {"label": "안전모 미착용", "bbox_xyxy": [50.0, 60.0, 70.0, 80.0],
                 "box_color": "red", "confidence": 0.78, "is_wearing": false},
                {"label": "안전벨트 착용", "bbox_xyxy": [90.0, 100.0, 110.0, 120.0],
                 "box_color": "blue", "confidence": 0.85, "is_wearing": true}
              ],
              "image_width": 1280,
              "image_height": 720
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

    // ─── 업로드 응답 ──────────────────────────────────────────────────────

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

    // ─── visionDetections 파싱 ────────────────────────────────────────────

    @Test
    void vision_validation이_있으면_imageWidth_imageHeight가_정확히_파싱된다() throws Exception {
        Session s = login(createUser("admin"));
        int projectId = createProject(s.cookie());
        insertFile(s.userId(), projectId, "site_photo", VISION_DETAIL);

        mockMvc.perform(get("/projects/{pid}/files", projectId).cookie(s.cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].visionDetections.imageWidth").value(677))
                .andExpect(jsonPath("$.data.items[0].visionDetections.imageHeight").value(493));
    }

    @Test
    void detections_label_boxColor_confidence_isWearing이_정확히_파싱된다() throws Exception {
        Session s = login(createUser("admin"));
        int projectId = createProject(s.cookie());
        insertFile(s.userId(), projectId, "site_photo", VISION_DETAIL);

        mockMvc.perform(get("/projects/{pid}/files", projectId).cookie(s.cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].visionDetections.detections[0].label").value("안전모 착용"))
                .andExpect(jsonPath("$.data.items[0].visionDetections.detections[0].boxColor").value("blue"))
                .andExpect(jsonPath("$.data.items[0].visionDetections.detections[0].confidence").value(0.8332))
                .andExpect(jsonPath("$.data.items[0].visionDetections.detections[0].isWearing").value(true));
    }

    @Test
    void bboxXyxy_좌표_4개가_순서대로_정확히_반환된다() throws Exception {
        Session s = login(createUser("admin"));
        int projectId = createProject(s.cookie());
        insertFile(s.userId(), projectId, "site_photo", VISION_DETAIL);

        mockMvc.perform(get("/projects/{pid}/files", projectId).cookie(s.cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].visionDetections.detections[0].bboxXyxy", hasSize(4)))
                .andExpect(jsonPath("$.data.items[0].visionDetections.detections[0].bboxXyxy[0]").value(241.49))
                .andExpect(jsonPath("$.data.items[0].visionDetections.detections[0].bboxXyxy[1]").value(33.61))
                .andExpect(jsonPath("$.data.items[0].visionDetections.detections[0].bboxXyxy[2]").value(431.7))
                .andExpect(jsonPath("$.data.items[0].visionDetections.detections[0].bboxXyxy[3]").value(278.23));
    }

    @Test
    void detections가_여러_건이면_전부_반환된다() throws Exception {
        Session s = login(createUser("admin"));
        int projectId = createProject(s.cookie());
        insertFile(s.userId(), projectId, "site_photo", MULTI_DETECTION_DETAIL);

        mockMvc.perform(get("/projects/{pid}/files", projectId).cookie(s.cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].visionDetections.detections", hasSize(3)))
                .andExpect(jsonPath("$.data.items[0].visionDetections.imageWidth").value(1280))
                .andExpect(jsonPath("$.data.items[0].visionDetections.imageHeight").value(720));
    }

    @Test
    void class_id_class_code_equipment_needs_review_등_내부_필드는_노출되지_않는다() throws Exception {
        Session s = login(createUser("admin"));
        int projectId = createProject(s.cookie());
        insertFile(s.userId(), projectId, "site_photo", VISION_DETAIL);

        MvcResult result = mockMvc.perform(get("/projects/{pid}/files", projectId).cookie(s.cookie()))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain("class_id", "class_code", "equipment", "needs_review",
                "result_code", "status_code", "original_filename", "usage_statement_id", "reason");
    }

    // ─── visionDetections null 케이스 ─────────────────────────────────────

    @Test
    void detail이_null이면_visionDetections가_null이다() throws Exception {
        Session s = login(createUser("admin"));
        int projectId = createProject(s.cookie());
        insertFile(s.userId(), projectId, "site_photo", null);

        mockMvc.perform(get("/projects/{pid}/files", projectId).cookie(s.cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].visionDetections").value(nullValue()));
    }

    @Test
    void vision_validation_키가_없는_JSON이면_visionDetections가_null이다() throws Exception {
        Session s = login(createUser("admin"));
        int projectId = createProject(s.cookie());
        insertFile(s.userId(), projectId, "site_photo", "{\"other_key\": {\"foo\": 1}}");

        mockMvc.perform(get("/projects/{pid}/files", projectId).cookie(s.cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].visionDetections").value(nullValue()));
    }

    @Test
    void site_photo가_아닌_타입은_vision_validation_없으므로_visionDetections가_null이다() throws Exception {
        Session s = login(createUser("admin"));
        int projectId = createProject(s.cookie());
        insertFile(s.userId(), projectId, "receipt", null);

        mockMvc.perform(get("/projects/{pid}/files", projectId).cookie(s.cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].visionDetections").value(nullValue()));
    }

    // ─── 혼재 케이스 ──────────────────────────────────────────────────────

    @Test
    void visionDetections_있는_파일과_없는_파일이_혼재하면_각각_정확히_반환된다() throws Exception {
        Session s = login(createUser("admin"));
        int projectId = createProject(s.cookie());

        insertFile(s.userId(), projectId, "site_photo", VISION_DETAIL);
        insertFile(s.userId(), projectId, "receipt", null);

        mockMvc.perform(get("/projects/{pid}/files", projectId).cookie(s.cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items", hasSize(2)));
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
        if (detailJson != null) {
            jdbcTemplate.update("""
                    INSERT INTO service.files
                        (project_id, uploaded_by_user_id, uploaded_evidence_type_code,
                         original_filename, storage_key, mime_type, size_bytes, detail)
                    VALUES (?, ?, ?, 'photo.jpg', ?, 'image/jpeg', 1024, ?::jsonb)
                    """, projectId, userId, evidenceTypeCode, "key-" + UUID.randomUUID(), detailJson);
        } else {
            jdbcTemplate.update("""
                    INSERT INTO service.files
                        (project_id, uploaded_by_user_id, uploaded_evidence_type_code,
                         original_filename, storage_key, mime_type, size_bytes)
                    VALUES (?, ?, ?, 'photo.jpg', ?, 'image/jpeg', 1024)
                    """, projectId, userId, evidenceTypeCode, "key-" + UUID.randomUUID());
        }
    }
}
