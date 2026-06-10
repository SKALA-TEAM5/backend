package com.skala.backend.evidence.controller;

import com.skala.backend.auth.security.AuthenticatedUser;
import com.skala.backend.evidence.dto.EvidenceRequests.LinkEvidenceFileRequest;
import com.skala.backend.evidence.dto.EvidenceRequests.MoveEvidenceFileLinkRequest;
import com.skala.backend.evidence.dto.EvidenceResponses.EvidenceLinkResponse;
import com.skala.backend.evidence.dto.EvidenceResponses.ItemEvidenceFilesResponse;
import com.skala.backend.evidence.service.EvidenceCommandService;
import com.skala.backend.evidence.service.EvidenceQueryService;
import com.skala.backend.file.dto.ProjectFileResponses.UploadAndLinkResponse;
import com.skala.backend.file.service.ProjectFileService;
import com.skala.backend.global.config.OpenApiConfig;
import com.skala.backend.global.response.ApiResponse;
import com.skala.backend.usage.dto.UsageStatementResponses.RequirementResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/projects/{projectId}")
@Tag(name = "프로젝트 증빙 연결", description = "사용내역서 상세항목과 증빙 파일 연결 API")
@SecurityRequirement(name = OpenApiConfig.COOKIE_AUTH)
public class ProjectEvidenceLinkController {

	private final EvidenceCommandService commandService;
	private final EvidenceQueryService queryService;
	private final ProjectFileService fileService;

	public ProjectEvidenceLinkController(EvidenceCommandService commandService, EvidenceQueryService queryService, ProjectFileService fileService) {
		this.commandService = commandService;
		this.queryService = queryService;
		this.fileService = fileService;
	}

	@PostMapping(value = "/usage-statement-items/{itemId}/evidence-files/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@Operation(
			summary = "증빙 파일 업로드 후 즉시 상세항목에 연결",
			description = """
					파일 업로드와 세부항목 증빙 연결을 단일 요청으로 처리합니다.

					**처리 순서**
					1. 파일을 스토리지에 저장하고 `files` 테이블에 등록합니다.
					2. 등록된 파일을 지정한 세부항목(`itemId`)에 즉시 연결합니다.

					두 작업은 단일 트랜잭션으로 처리되므로 연결 실패 시 파일 등록도 롤백됩니다.

					**사용내역서 상태 변환**
					세부항목이 속한 사용내역서가 `supplement_required` 상태인 경우, 연결 성공 후 자동으로 `draft`로 복귀합니다.
					"""
	)
	public ResponseEntity<ApiResponse<UploadAndLinkResponse>> uploadAndLink(
			@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser currentUser,
			@PathVariable Long projectId,
			@PathVariable Long itemId,
			@Parameter(
					description = "업로드 파일의 증빙 유형 코드입니다.",
					example = "receipt",
					schema = @Schema(allowableValues = {
							"receipt", "tax_invoice", "tax_invoice_confirm", "third_party_lookup",
							"transaction_statement", "site_photo", "item_photo", "wearing_photo",
							"work_photo", "appointment_report", "pay_stub", "work_log",
							"daily_output_log", "inspection_log", "supply_ledger", "inventory_ledger",
							"edu_confirm", "edu_attendance", "transfer_confirm", "health_checkup_result",
							"health_checkup_contract", "tech_guidance_contract", "tech_guidance_report",
							"tech_guidance_photo", "usage_statement", "analysis_table", "purchase_detail",
							"other_document"
					})
			)
			@RequestParam String evidenceTypeCode,
			@RequestParam MultipartFile file,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant capturedAt
	) {
		UploadAndLinkResponse response = fileService.uploadAndLink(currentUser.id(), projectId, itemId, evidenceTypeCode, file, capturedAt);
		return ResponseEntity.ok(ApiResponse.success(response, "파일 업로드 및 증빙 연결에 성공했습니다."));
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

	@GetMapping({
			"/usage-statement-items/{itemId}/evidence-requirements",
			"/usage-statements/{usageStatementId}/line-items/{itemId}/evidence-requirements"
	})
	@Operation(
			tags = {"에이전트 경고"},
			summary = "상세항목 서류 충족 현황 조회",
			description = """
					safety-doc 에이전트가 판정한 서류 제출 현황을 반환합니다.

					**사용 시나리오**
					에이전트 경고 목록(`GET /agents/warnings`)에서 `agentTypeCode = 'safety-doc'`인 경고를 \
					클릭했을 때, 해당 항목에 어떤 서류가 부족한지 상세하게 확인하기 위해 호출합니다.

					**응답 해석**
					- `satisfied = false` → 아직 제출되지 않은 서류 (보완 필요)
					- `satisfied = true`  → 제출 완료된 서류
					- `is_active = false`인 항목은 제외됩니다 (에이전트 재실행 시 무효화된 이전 판정).
					"""
	)
	public ResponseEntity<ApiResponse<List<RequirementResponse>>> listRequirements(
			@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser currentUser,
			@PathVariable Long projectId,
			@PathVariable Long itemId
	) {
		List<RequirementResponse> response = queryService.listRequirements(currentUser.id(), projectId, itemId);
		return ResponseEntity.ok(ApiResponse.success(response, "서류 충족 현황 조회에 성공했습니다."));
	}
}
