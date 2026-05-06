package com.skala.backend.evidence.controller;

import com.skala.backend.auth.security.AuthenticatedUser;
import com.skala.backend.evidence.dto.EvidenceRequests.LinkEvidenceFileRequest;
import com.skala.backend.evidence.dto.EvidenceRequests.MoveEvidenceFileLinkRequest;
import com.skala.backend.evidence.dto.EvidenceResponses.EvidenceLinkResponse;
import com.skala.backend.evidence.dto.EvidenceResponses.ItemEvidenceFilesResponse;
import com.skala.backend.evidence.service.EvidenceCommandService;
import com.skala.backend.evidence.service.EvidenceQueryService;
import com.skala.backend.global.config.OpenApiConfig;
import com.skala.backend.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/projects/{projectId}")
@Tag(name = "프로젝트 증빙 연결", description = "사용내역서 상세항목과 증빙 파일 연결 API")
@SecurityRequirement(name = OpenApiConfig.COOKIE_AUTH)
public class ProjectEvidenceLinkController {

	private final EvidenceCommandService commandService;
	private final EvidenceQueryService queryService;

	public ProjectEvidenceLinkController(EvidenceCommandService commandService, EvidenceQueryService queryService) {
		this.commandService = commandService;
		this.queryService = queryService;
	}

	@PostMapping("/usage-statement-items/{itemId}/evidence-files")
	@Operation(summary = "상세항목에 증빙 파일 연결")
	public ResponseEntity<ApiResponse<EvidenceLinkResponse>> linkFile(
			@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser currentUser,
			@PathVariable Long projectId,
			@PathVariable Long itemId,
			@Valid @RequestBody LinkEvidenceFileRequest request
	) {
		EvidenceLinkResponse response = commandService.linkFile(currentUser.id(), projectId, itemId, request);
		return ResponseEntity.ok(ApiResponse.success(response, "증빙 파일 연결에 성공했습니다."));
	}

	@PatchMapping("/evidence-file-links/{linkId}")
	@Operation(summary = "증빙 파일 연결 이동")
	public ResponseEntity<ApiResponse<EvidenceLinkResponse>> moveLink(
			@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser currentUser,
			@PathVariable Long projectId,
			@PathVariable Long linkId,
			@Valid @RequestBody MoveEvidenceFileLinkRequest request
	) {
		EvidenceLinkResponse response = commandService.moveLink(currentUser.id(), projectId, linkId, request);
		return ResponseEntity.ok(ApiResponse.success(response, "증빙 파일 연결 이동에 성공했습니다."));
	}

	@DeleteMapping("/evidence-file-links/{linkId}")
	@Operation(summary = "증빙 파일 연결 삭제")
	public ResponseEntity<ApiResponse<Void>> deleteLink(
			@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser currentUser,
			@PathVariable Long projectId,
			@PathVariable Long linkId
	) {
		commandService.deleteLink(currentUser.id(), projectId, linkId);
		return ResponseEntity.ok(ApiResponse.success(null, "증빙 파일 연결 삭제에 성공했습니다."));
	}

	@GetMapping("/usage-statement-items/{itemId}/evidence-files")
	@Operation(summary = "상세항목별 증빙 파일 목록 조회")
	public ResponseEntity<ApiResponse<ItemEvidenceFilesResponse>> listItemFiles(
			@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser currentUser,
			@PathVariable Long projectId,
			@PathVariable Long itemId
	) {
		ItemEvidenceFilesResponse response = queryService.listItemFiles(currentUser.id(), projectId, itemId);
		return ResponseEntity.ok(ApiResponse.success(response, "상세항목 증빙 파일 조회에 성공했습니다."));
	}
}
