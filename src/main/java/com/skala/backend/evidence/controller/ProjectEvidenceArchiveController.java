package com.skala.backend.evidence.controller;

import com.skala.backend.auth.security.AuthenticatedUser;
import com.skala.backend.evidence.dto.EvidenceResponses.ArchiveCategoryListResponse;
import com.skala.backend.evidence.dto.EvidenceResponses.ArchiveItemListResponse;
import com.skala.backend.evidence.service.EvidenceArchiveService;
import com.skala.backend.global.config.OpenApiConfig;
import com.skala.backend.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/projects/{projectId}/archive")
@Tag(name = "프로젝트 아카이브", description = "프로젝트 아카이브 조회 API")
@SecurityRequirement(name = OpenApiConfig.COOKIE_AUTH)
public class ProjectEvidenceArchiveController {

	private final EvidenceArchiveService archiveService;

	public ProjectEvidenceArchiveController(EvidenceArchiveService archiveService) {
		this.archiveService = archiveService;
	}

	@GetMapping("/categories")
	@Operation(summary = "아카이브 카테고리 요약 조회")
	public ResponseEntity<ApiResponse<ArchiveCategoryListResponse>> listCategories(
			@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser currentUser,
			@PathVariable Long projectId,
			@RequestParam(required = false) Long usageStatementId,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate reportMonth
	) {
		ArchiveCategoryListResponse response = archiveService.listCategories(
				currentUser.id(),
				projectId,
				usageStatementId,
				reportMonth
		);
		return ResponseEntity.ok(ApiResponse.success(response, "아카이브 카테고리 요약 조회에 성공했습니다."));
	}

	@GetMapping("/categories/{categoryCode}/items")
	@Operation(summary = "아카이브 카테고리별 상세항목 조회")
	public ResponseEntity<ApiResponse<ArchiveItemListResponse>> listCategoryItems(
			@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser currentUser,
			@PathVariable Long projectId,
			@PathVariable String categoryCode,
			@RequestParam(required = false) Long usageStatementId,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate reportMonth
	) {
		ArchiveItemListResponse response = archiveService.listCategoryItems(
				currentUser.id(),
				projectId,
				categoryCode,
				usageStatementId,
				reportMonth
		);
		return ResponseEntity.ok(ApiResponse.success(response, "아카이브 상세항목 조회에 성공했습니다."));
	}
}
