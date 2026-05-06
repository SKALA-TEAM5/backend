package com.skala.backend.file.controller;

import com.skala.backend.auth.security.AuthenticatedUser;
import com.skala.backend.file.dto.ProjectFileResponses.ProjectFileListResponse;
import com.skala.backend.file.dto.ProjectFileResponses.ProjectFileUploadResponse;
import com.skala.backend.file.service.ProjectFileService;
import com.skala.backend.file.service.ProjectFileService.FileResource;
import com.skala.backend.global.config.OpenApiConfig;
import com.skala.backend.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/projects/{projectId}/files")
@Tag(name = "프로젝트 파일", description = "프로젝트 증빙 파일 조회, 업로드, 다운로드 API")
@SecurityRequirement(name = OpenApiConfig.COOKIE_AUTH)
public class ProjectFileController {

	private final ProjectFileService projectFileService;

	public ProjectFileController(ProjectFileService projectFileService) {
		this.projectFileService = projectFileService;
	}

	@GetMapping
	@Operation(summary = "프로젝트 파일 목록 조회")
	public ResponseEntity<ApiResponse<ProjectFileListResponse>> listFiles(
			@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser currentUser,
			@PathVariable Long projectId,
			@RequestParam(required = false) String evidenceTypeCode,
			@RequestParam(required = false) Boolean linked,
			@RequestParam(required = false, defaultValue = "1") Integer page,
			@RequestParam(required = false, defaultValue = "20") Integer size
	) {
		ProjectFileListResponse response = projectFileService.listFiles(
				currentUser.id(),
				projectId,
				evidenceTypeCode,
				linked,
				page,
				size
		);
		return ResponseEntity.ok(ApiResponse.success(response, "프로젝트 파일 목록 조회에 성공했습니다."));
	}

	@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@Operation(summary = "프로젝트 파일 업로드")
	public ResponseEntity<ApiResponse<ProjectFileUploadResponse>> upload(
			@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser currentUser,
			@PathVariable Long projectId,
			@RequestParam String evidenceTypeCode,
			@RequestParam MultipartFile file,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant capturedAt
	) {
		ProjectFileUploadResponse response = projectFileService.upload(
				currentUser.id(),
				projectId,
				evidenceTypeCode,
				file,
				capturedAt
		);
		return ResponseEntity.ok(ApiResponse.success(response, "파일 업로드에 성공했습니다."));
	}

	@GetMapping("/{fileId}/download")
	@Operation(summary = "프로젝트 파일 다운로드")
	public ResponseEntity<org.springframework.core.io.Resource> download(
			@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser currentUser,
			@PathVariable Long projectId,
			@PathVariable Long fileId
	) {
		return fileResponse(projectFileService.download(currentUser.id(), projectId, fileId));
	}

	@GetMapping("/{fileId}/preview")
	@Operation(summary = "프로젝트 파일 미리보기")
	public ResponseEntity<org.springframework.core.io.Resource> preview(
			@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser currentUser,
			@PathVariable Long projectId,
			@PathVariable Long fileId
	) {
		return fileResponse(projectFileService.preview(currentUser.id(), projectId, fileId));
	}

	@DeleteMapping("/{fileId}")
	@Operation(summary = "프로젝트 파일 삭제")
	public ResponseEntity<ApiResponse<Void>> delete(
			@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser currentUser,
			@PathVariable Long projectId,
			@PathVariable Long fileId
	) {
		projectFileService.delete(currentUser.id(), projectId, fileId);
		return ResponseEntity.ok(ApiResponse.success(null, "파일 삭제에 성공했습니다."));
	}

	private ResponseEntity<org.springframework.core.io.Resource> fileResponse(FileResource file) {
		ContentDisposition disposition = (file.inline() ? ContentDisposition.inline() : ContentDisposition.attachment())
				.filename(file.filename(), StandardCharsets.UTF_8)
				.build();
		return ResponseEntity.ok()
				.contentType(MediaType.parseMediaType(file.mimeType()))
				.header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
				.body(file.resource());
	}
}
