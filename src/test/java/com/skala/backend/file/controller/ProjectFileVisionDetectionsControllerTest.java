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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GET /projects/{projectId}/files/{fileId}/vision-detections 엔드포인트 검증.
 * 세부내역 파일보기(evidence-files)와 동일한 파서를 공유하므로 파싱 동작은 일치해야 한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProjectFileVisionDetectionsControllerTest {

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
              "original_filename": "photo.png",
              "usage_statement_id": 2
            }}
            """;

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;

    @MockitoBean
    MinioClient minioClient;

    private static final String DETECTIONS = "$.data.visionDetections";

    @Test
    void wearing_photo에_vision_validation이_있으면_imageWidth_imageHeight가_파싱된다() throws Exception {
        Ctx ctx = setup(VISION_DETAIL, "wearing_photo");

        mockMvc.perform(get("/projects/{pid}/files/{fid}/vision-detections", ctx.projectId, ctx.fileId)
                        .cookie(ctx.cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fileId").value(ctx.fileId))
                .andExpect(jsonPath(DETECTIONS + ".imageWidth").value(677))
                .andExpect(jsonPath(DETECTIONS + ".imageHeight").value(493));
    }

    @Test
    void detections_label_boxColor_confidence_isWearing_bbox가_정확히_파싱된다() throws Exception {
        Ctx ctx = setup(VISION_DETAIL, "wearing_photo");

        mockMvc.perform(get("/projects/{pid}/files/{fid}/vision-detections", ctx.projectId, ctx.fileId)
                        .cookie(ctx.cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath(DETECTIONS + ".detections", hasSize(1)))
                .andExpect(jsonPath(DETECTIONS + ".detections[0].label").value("안전모 착용"))
                .andExpect(jsonPath(DETECTIONS + ".detections[0].boxColor").value("blue"))
                .andExpect(jsonPath(DETECTIONS + ".detections[0].confidence").value(0.8332))
                .andExpect(jsonPath(DETECTIONS + ".detections[0].isWearing").value(true))
                .andExpect(jsonPath(DETECTIONS + ".detections[0].bboxXyxy", hasSize(4)))
                .andExpect(jsonPath(DETECTIONS + ".detections[0].bboxXyxy[0]").value(241.49))
                .andExpect(jsonPath(DETECTIONS + ".detections[0].bboxXyxy[3]").value(278.23));
    }

    @Test
    void 내부_필드는_응답에_노출되지_않는다() throws Exception {
        Ctx ctx = setup(VISION_DETAIL, "wearing_photo");

        MvcResult result = mockMvc.perform(get("/projects/{pid}/files/{fid}/vision-detections", ctx.projectId, ctx.fileId)
                        .cookie(ctx.cookie))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain("class_id", "class_code", "equipment", "needs_review",
                "result_code", "status_code", "is_appropriate", "usage_statement_id");
    }

    @Test
    void wearing_photo가_아니면_vision_validation이_있어도_visionDetections가_null이다() throws Exception {
        Ctx ctx = setup(VISION_DETAIL, "site_photo");

        mockMvc.perform(get("/projects/{pid}/files/{fid}/vision-detections", ctx.projectId, ctx.fileId)
                        .cookie(ctx.cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath(DETECTIONS).value(nullValue()));
    }

    @Test
    void detail이_null이면_visionDetections가_null이다() throws Exception {
        Ctx ctx = setup(null, "wearing_photo");

        mockMvc.perform(get("/projects/{pid}/files/{fid}/vision-detections", ctx.projectId, ctx.fileId)
                        .cookie(ctx.cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath(DETECTIONS).value(nullValue()));
    }

    @Test
    void vision_validation_키가_없는_JSON이면_visionDetections가_null이다() throws Exception {
        Ctx ctx = setup("{\"other_key\": {\"foo\": 1}}", "wearing_photo");

        mockMvc.perform(get("/projects/{pid}/files/{fid}/vision-detections", ctx.projectId, ctx.fileId)
                        .cookie(ctx.cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath(DETECTIONS).value(nullValue()));
    }

    @Test
    void 다른_프로젝트의_fileId를_사용하면_404다() throws Exception {
        Ctx ctx1 = setup(VISION_DETAIL, "wearing_photo");
        Ctx ctx2 = setup(VISION_DETAIL, "wearing_photo");

        mockMvc.perform(get("/projects/{pid}/files/{fid}/vision-detections", ctx1.projectId, ctx2.fileId)
                        .cookie(ctx1.cookie))
                .andExpect(status().isNotFound());
    }

    @Test
    void 존재하지_않는_fileId는_404다() throws Exception {
        Ctx ctx = setup(VISION_DETAIL, "wearing_photo");

        mockMvc.perform(get("/projects/{pid}/files/{fid}/vision-detections", ctx.projectId, Long.MAX_VALUE)
                        .cookie(ctx.cookie))
                .andExpect(status().isNotFound());
    }

    @Test
    void 인증_없이_접근하면_401이다() throws Exception {
        Ctx ctx = setup(VISION_DETAIL, "wearing_photo");

        mockMvc.perform(get("/projects/{pid}/files/{fid}/vision-detections", ctx.projectId, ctx.fileId))
                .andExpect(status().isUnauthorized());
    }

    // ─── fixtures ─────────────────────────────────────────────────────────

    private record Ctx(Cookie cookie, int projectId, long fileId) {}

    private Ctx setup(String detailJson, String evidenceTypeCode) throws Exception {
        Map<String, String> credentials = createUser("admin");
        Cookie cookie = loginCookie(credentials);
        int userId = getUserId(credentials.get("employeeNo"));
        int projectId = createProject(cookie);
        long fileId = insertFile(userId, projectId, evidenceTypeCode, detailJson);
        return new Ctx(cookie, projectId, fileId);
    }

    private Map<String, String> createUser(String roleCode) {
        String empNo = "EMP-" + UUID.randomUUID();
        userRepository.saveAndFlush(User.create(
                empNo, "홍길동",
                passwordEncoder.encode("P@ssw0rd123!"),
                RoleCode.from(roleCode)
        ));
        return Map.of("employeeNo", empNo, "password", "P@ssw0rd123!");
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

    private int getUserId(String employeeNo) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM service.users WHERE employee_no = ?", Integer.class, employeeNo);
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

    private long insertFile(int userId, int projectId, String evidenceTypeCode, String detailJson) {
        if (detailJson != null) {
            return jdbcTemplate.queryForObject("""
                    INSERT INTO service.files
                        (project_id, uploaded_by_user_id, storage_key, original_filename, mime_type,
                         size_bytes, uploaded_evidence_type_code, status_code, detail)
                    VALUES (?, ?, ?, 'photo.jpg', 'image/jpeg', 1024, ?, 'success', ?::jsonb)
                    RETURNING id
                    """, Long.class, projectId, userId, "key-" + UUID.randomUUID(), evidenceTypeCode, detailJson);
        }
        return jdbcTemplate.queryForObject("""
                INSERT INTO service.files
                    (project_id, uploaded_by_user_id, storage_key, original_filename, mime_type,
                     size_bytes, uploaded_evidence_type_code, status_code)
                VALUES (?, ?, ?, 'photo.jpg', 'image/jpeg', 1024, ?, 'success')
                RETURNING id
                """, Long.class, projectId, userId, "key-" + UUID.randomUUID(), evidenceTypeCode);
    }
}
