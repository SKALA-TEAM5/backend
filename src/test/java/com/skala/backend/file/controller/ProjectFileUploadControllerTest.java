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
class ProjectFileUploadControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;

    @MockitoBean
    MinioClient minioClient;

    // ─── 사용내역서 PDF 가드 ───────────────────────────────────────────────

    @Test
    void 사용내역서로_JPG_업로드_시_400() throws Exception {
        Cookie cookie = loginCookie(createUser("admin"));
        int projectId = createProject(cookie);
        MockMultipartFile file = new MockMultipartFile("file", "statement.jpg", "image/jpeg", "img".getBytes());

        mockMvc.perform(multipart("/projects/{pid}/files", projectId)
                        .file(file)
                        .param("evidenceTypeCode", "usage_statement")
                        .cookie(cookie))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("사용내역서는 PDF 파일만 업로드할 수 있습니다."));
    }

    @Test
    void 사용내역서로_확장자만_pdf인_위장파일_업로드_시_400() throws Exception {
        Cookie cookie = loginCookie(createUser("admin"));
        int projectId = createProject(cookie);
        // .pdf 확장자 + application/pdf MIME 이지만 내용은 JPG (매직넘버 불일치)
        MockMultipartFile file = new MockMultipartFile("file", "fake.pdf", "application/pdf",
                new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0});

        mockMvc.perform(multipart("/projects/{pid}/files", projectId)
                        .file(file)
                        .param("evidenceTypeCode", "usage_statement")
                        .cookie(cookie))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("사용내역서는 PDF 파일만 업로드할 수 있습니다."));
    }

    @Test
    void 사용내역서로_정상_PDF_업로드_시_200() throws Exception {
        Cookie cookie = loginCookie(createUser("admin"));
        int projectId = createProject(cookie);
        MockMultipartFile file = new MockMultipartFile("file", "statement.pdf", "application/pdf",
                "%PDF-1.7\n...".getBytes());

        mockMvc.perform(multipart("/projects/{pid}/files", projectId)
                        .file(file)
                        .param("evidenceTypeCode", "usage_statement")
                        .cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fileId").isNumber());
    }

    @Test
    void 영수증으로_JPG_업로드는_가드_영향없이_200() throws Exception {
        Cookie cookie = loginCookie(createUser("admin"));
        int projectId = createProject(cookie);
        MockMultipartFile file = new MockMultipartFile("file", "receipt.jpg", "image/jpeg", "img".getBytes());

        mockMvc.perform(multipart("/projects/{pid}/files", projectId)
                        .file(file)
                        .param("evidenceTypeCode", "receipt")
                        .cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fileId").isNumber());
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
}
