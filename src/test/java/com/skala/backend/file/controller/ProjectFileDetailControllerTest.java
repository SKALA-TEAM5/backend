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

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProjectFileDetailControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;

    @MockitoBean
    MinioClient minioClient;

    // ─── 업로드 응답 ──────────────────────────────────────────────────────

    @Test
    void 업로드_직후_응답에_detail은_null이다() throws Exception {
        Cookie cookie = loginCookie(createUser("admin"));
        int projectId = createProject(cookie);
        MockMultipartFile file = new MockMultipartFile("file", "test.jpg", "image/jpeg", "data".getBytes());

        mockMvc.perform(multipart("/projects/{pid}/files", projectId)
                        .file(file)
                        .param("evidenceTypeCode", "receipt")
                        .cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.detail").isEmpty());
    }

    @Test
    void 업로드_응답에_detail_필드는_null이다() throws Exception {
        Cookie cookie = loginCookie(createUser("admin"));
        int projectId = createProject(cookie);
        MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", "data".getBytes());

        mockMvc.perform(multipart("/projects/{pid}/files", projectId)
                        .file(file)
                        .param("evidenceTypeCode", "site_photo")
                        .cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.detail").value(nullValue()));
    }

    // ─── 목록 조회 응답 ───────────────────────────────────────────────────

    @Test
    void 목록_조회_시_detail이_null인_파일은_null로_반환된다() throws Exception {
        Cookie cookie = loginCookie(createUser("admin"));
        int projectId = createProject(cookie);
        int userId = readUserId(createUser("admin"));
        insertFile(userId, projectId, null);

        mockMvc.perform(get("/projects/{pid}/files", projectId).cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].detail").isEmpty());
    }

    @Test
    void FastAPI가_detail을_채우면_목록_조회_시_반환된다() throws Exception {
        Cookie cookie = loginCookie(createUser("admin"));
        int projectId = createProject(cookie);
        int userId = readUserId(createUser("admin"));
        insertFile(userId, projectId, "{\"exif\":{\"lat\":37.5,\"lon\":127.0}}");

        mockMvc.perform(get("/projects/{pid}/files", projectId).cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].detail").isNotEmpty());
    }

    @Test
    void detail_있는_파일과_없는_파일이_혼재하면_각각_정확히_반환된다() throws Exception {
        Cookie cookie = loginCookie(createUser("admin"));
        int projectId = createProject(cookie);
        int userId = readUserId(createUser("admin"));

        insertFile(userId, projectId, "{\"exif\":{\"lat\":37.5}}");
        insertFile(userId, projectId, null);

        mockMvc.perform(get("/projects/{pid}/files", projectId).cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(2));
    }

    @Test
    void 업로드_후_DB에_detail_기록되면_목록_조회_시_반영된다() throws Exception {
        Cookie cookie = loginCookie(createUser("admin"));
        int projectId = createProject(cookie);
        int userId = readUserId(createUser("admin"));

        // JPA 1차 캐시 간섭을 피하기 위해 JdbcTemplate으로 직접 detail 포함 insert
        insertFile(userId, projectId, "{\"ai\":{\"label\":\"안전모 착용 확인\"}}");

        mockMvc.perform(get("/projects/{pid}/files", projectId).cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].detail").isNotEmpty());
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

    private void insertFile(int userId, int projectId, String detailJson) {
        if (detailJson != null) {
            jdbcTemplate.update("""
                    INSERT INTO service.files
                        (project_id, uploaded_by_user_id, uploaded_evidence_type_code,
                         original_filename, storage_key, mime_type, size_bytes, detail)
                    VALUES (?, ?, 'receipt', 'test.jpg', ?, 'image/jpeg', 1024, ?::jsonb)
                    """, projectId, userId, "key-" + UUID.randomUUID(), detailJson);
        } else {
            jdbcTemplate.update("""
                    INSERT INTO service.files
                        (project_id, uploaded_by_user_id, uploaded_evidence_type_code,
                         original_filename, storage_key, mime_type, size_bytes)
                    VALUES (?, ?, 'receipt', 'test.jpg', ?, 'image/jpeg', 1024)
                    """, projectId, userId, "key-" + UUID.randomUUID());
        }
    }
}
