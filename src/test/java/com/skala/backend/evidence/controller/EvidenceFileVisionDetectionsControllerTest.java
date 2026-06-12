package com.skala.backend.evidence.controller;

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
 * 세부내역 파일보기(GET .../usage-statement-items/{itemId}/evidence-files) 응답에
 * files.detail의 vision_validation이 visionDetections로 파싱되어 포함되는지 검증한다.
 * 파일 목록 API와 동일한 파서를 공유하므로 응답 형태가 일치해야 한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class EvidenceFileVisionDetectionsControllerTest {

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

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;

    @MockitoBean
    MinioClient minioClient;

    private static final String FILES = "$.data.files[0].visionDetections";

    @Test
    void vision_validation이_있는_증빙파일은_imageWidth_imageHeight가_파싱된다() throws Exception {
        Ctx ctx = setup(VISION_DETAIL, "wearing_photo");

        mockMvc.perform(get("/projects/{pid}/usage-statement-items/{iid}/evidence-files", ctx.projectId, ctx.itemId)
                        .cookie(ctx.cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.files", hasSize(1)))
                .andExpect(jsonPath(FILES + ".imageWidth").value(677))
                .andExpect(jsonPath(FILES + ".imageHeight").value(493));
    }

    @Test
    void detections_label_boxColor_confidence_isWearing_bbox가_정확히_파싱된다() throws Exception {
        Ctx ctx = setup(VISION_DETAIL, "wearing_photo");

        mockMvc.perform(get("/projects/{pid}/usage-statement-items/{iid}/evidence-files", ctx.projectId, ctx.itemId)
                        .cookie(ctx.cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath(FILES + ".detections[0].label").value("안전모 착용"))
                .andExpect(jsonPath(FILES + ".detections[0].boxColor").value("blue"))
                .andExpect(jsonPath(FILES + ".detections[0].confidence").value(0.8332))
                .andExpect(jsonPath(FILES + ".detections[0].isWearing").value(true))
                .andExpect(jsonPath(FILES + ".detections[0].bboxXyxy", hasSize(4)))
                .andExpect(jsonPath(FILES + ".detections[0].bboxXyxy[0]").value(241.49))
                .andExpect(jsonPath(FILES + ".detections[0].bboxXyxy[3]").value(278.23));
    }

    @Test
    void 내부_필드는_세부내역_응답에도_노출되지_않는다() throws Exception {
        Ctx ctx = setup(VISION_DETAIL, "wearing_photo");

        MvcResult result = mockMvc.perform(get("/projects/{pid}/usage-statement-items/{iid}/evidence-files", ctx.projectId, ctx.itemId)
                        .cookie(ctx.cookie))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain("class_id", "class_code", "equipment", "needs_review",
                "result_code", "status_code", "is_appropriate", "usage_statement_id");
    }

    @Test
    void detail이_null이면_visionDetections가_null이다() throws Exception {
        Ctx ctx = setup(null, "receipt");

        mockMvc.perform(get("/projects/{pid}/usage-statement-items/{iid}/evidence-files", ctx.projectId, ctx.itemId)
                        .cookie(ctx.cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.files", hasSize(1)))
                .andExpect(jsonPath(FILES).value(nullValue()));
    }

    @Test
    void wearing_photo가_아니면_vision_validation이_있어도_visionDetections가_null이다() throws Exception {
        Ctx ctx = setup(VISION_DETAIL, "site_photo");

        mockMvc.perform(get("/projects/{pid}/usage-statement-items/{iid}/evidence-files", ctx.projectId, ctx.itemId)
                        .cookie(ctx.cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.files", hasSize(1)))
                .andExpect(jsonPath(FILES).value(nullValue()));
    }

    @Test
    void vision_validation_키가_없는_JSON이면_visionDetections가_null이다() throws Exception {
        Ctx ctx = setup("{\"other_key\": {\"foo\": 1}}", "wearing_photo");

        mockMvc.perform(get("/projects/{pid}/usage-statement-items/{iid}/evidence-files", ctx.projectId, ctx.itemId)
                        .cookie(ctx.cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath(FILES).value(nullValue()));
    }

    // ─── fixtures ─────────────────────────────────────────────────────────

    private record Ctx(Cookie cookie, int projectId, int itemId) {}

    private Ctx setup(String detailJson, String evidenceTypeCode) throws Exception {
        Map<String, String> user = createUser("admin");
        Cookie cookie = loginCookie(user);
        int userId = getUserId(user.get("employeeNo"));
        int projectId = createProject(cookie);
        int statementId = insertStatement(projectId);
        int itemId = insertItem(statementId);
        int fileId = insertFile(projectId, userId, evidenceTypeCode, detailJson);
        // 항목-파일 연결: 세부내역 파일보기는 연결된 파일만 노출한다.
        mockMvc.perform(post("/projects/{pid}/usage-statement-items/{iid}/evidence-files", projectId, itemId)
                        .cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("fileId", fileId, "evidenceTypeCode", evidenceTypeCode))))
                .andExpect(status().isOk());
        return new Ctx(cookie, projectId, itemId);
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

    private int insertStatement(int projectId) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO service.usage_statements
                    (project_id, report_month, revision_no, document_written_date, cumulative_progress_rate, status_code)
                VALUES (?, '2026-05-01', 1, '2026-05-01', 30, 'draft')
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

    private int getUserId(String employeeNo) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM service.users WHERE employee_no = ?", Integer.class, employeeNo);
    }

    private int insertFile(int projectId, int uploadedByUserId, String evidenceTypeCode, String detailJson) {
        if (detailJson != null) {
            return jdbcTemplate.queryForObject("""
                    INSERT INTO service.files
                        (project_id, uploaded_by_user_id, storage_key, original_filename, mime_type,
                         size_bytes, uploaded_evidence_type_code, status_code, detail)
                    VALUES (?, ?, ?, 'photo.jpg', 'image/jpeg', 1024, ?, 'success', ?::jsonb)
                    RETURNING id
                    """, Integer.class, projectId, uploadedByUserId, "key-" + UUID.randomUUID(), evidenceTypeCode, detailJson);
        }
        return jdbcTemplate.queryForObject("""
                INSERT INTO service.files
                    (project_id, uploaded_by_user_id, storage_key, original_filename, mime_type,
                     size_bytes, uploaded_evidence_type_code, status_code)
                VALUES (?, ?, ?, 'photo.jpg', 'image/jpeg', 1024, ?, 'success')
                RETURNING id
                """, Integer.class, projectId, uploadedByUserId, "key-" + UUID.randomUUID(), evidenceTypeCode);
    }
}
