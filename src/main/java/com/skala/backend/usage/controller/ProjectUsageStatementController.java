package com.skala.backend.usage.controller;

import com.skala.backend.auth.security.AuthenticatedUser;
import com.skala.backend.global.config.OpenApiConfig;
import com.skala.backend.global.response.ApiResponse;
import com.skala.backend.usage.dto.UsageStatementResponses.LatestUsageStatementResponse;
import com.skala.backend.usage.dto.UsageStatementResponses.UsageStatementDetailDataResponse;
import com.skala.backend.usage.dto.UsageStatementResponses.UsageStatementListResponse;
import com.skala.backend.usage.dto.UsageStatementResponses.UsageStatementStatusResponse;
import com.skala.backend.usage.service.UsageStatementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/projects/{projectId}/usage-statements")
@Tag(name = "프로젝트 사용내역서", description = "프로젝트 상세 페이지 사용내역서 탭 API")
@SecurityRequirement(name = OpenApiConfig.COOKIE_AUTH)
public class ProjectUsageStatementController {

	private final UsageStatementService usageStatementService;

	public ProjectUsageStatementController(UsageStatementService usageStatementService) {
		this.usageStatementService = usageStatementService;
	}

	@GetMapping("/latest")
	@Operation(summary = "최신 사용내역서 상세 조회")
	public ResponseEntity<ApiResponse<LatestUsageStatementResponse>> getLatest(
			@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser currentUser,
			@PathVariable Long projectId
	) {
		LatestUsageStatementResponse response = usageStatementService.getLatest(currentUser.id(), projectId);
		return ResponseEntity.ok(ApiResponse.success(response, "최신 사용내역서 조회에 성공했습니다."));
	}

	@GetMapping
	@Operation(summary = "프로젝트 사용내역서 목록 조회")
	public ResponseEntity<ApiResponse<UsageStatementListResponse>> list(
			@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser currentUser,
			@PathVariable Long projectId
	) {
		UsageStatementListResponse response = usageStatementService.list(currentUser.id(), projectId);
		return ResponseEntity.ok(ApiResponse.success(response, "사용내역서 목록 조회에 성공했습니다."));
	}

	@GetMapping("/by-month")
	@Operation(summary = "연월 기준 사용내역서 상세 조회")
	public ResponseEntity<ApiResponse<UsageStatementDetailDataResponse>> getByMonth(
			@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser currentUser,
			@PathVariable Long projectId,
			@RequestParam int year,
			@RequestParam int month
	) {
		UsageStatementDetailDataResponse response = usageStatementService.getByMonth(currentUser.id(), projectId, year, month);
		return ResponseEntity.ok(ApiResponse.success(response, "사용내역서 조회에 성공했습니다."));
	}

	@GetMapping("/{usageStatementId}")
	@Operation(summary = "특정 사용내역서 상세 조회")
	public ResponseEntity<ApiResponse<UsageStatementDetailDataResponse>> getDetail(
			@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser currentUser,
			@PathVariable Long projectId,
			@PathVariable Long usageStatementId
	) {
		UsageStatementDetailDataResponse response = usageStatementService.getDetail(currentUser.id(), projectId, usageStatementId);
		return ResponseEntity.ok(ApiResponse.success(response, "사용내역서 조회에 성공했습니다."));
	}

	@PatchMapping("/{usageStatementId}/submit")
	@Operation(summary = "사용내역서 제출 (R-33)")
	public ResponseEntity<ApiResponse<UsageStatementStatusResponse>> submit(
			@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser currentUser,
			@PathVariable Long projectId,
			@PathVariable Long usageStatementId
	) {
		UsageStatementStatusResponse response = usageStatementService.submit(currentUser.id(), projectId, usageStatementId);
		return ResponseEntity.ok(ApiResponse.success(response, "사용내역서가 제출되었습니다."));
	}

	@PatchMapping("/{usageStatementId}/request-supplement")
	@Operation(summary = "보완 요청 (R-34)")
	public ResponseEntity<ApiResponse<UsageStatementStatusResponse>> requestSupplement(
			@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser currentUser,
			@PathVariable Long projectId,
			@PathVariable Long usageStatementId
	) {
		UsageStatementStatusResponse response = usageStatementService.requestSupplement(currentUser.id(), projectId, usageStatementId);
		return ResponseEntity.ok(ApiResponse.success(response, "보완 요청이 완료되었습니다."));
	}

	@PatchMapping("/{usageStatementId}/complete-review")
	@Operation(summary = "최종 승인 (R-35)")
	public ResponseEntity<ApiResponse<UsageStatementStatusResponse>> completeReview(
			@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser currentUser,
			@PathVariable Long projectId,
			@PathVariable Long usageStatementId
	) {
		UsageStatementStatusResponse response = usageStatementService.completeReview(currentUser.id(), projectId, usageStatementId);
		return ResponseEntity.ok(ApiResponse.success(response, "최종 승인이 완료되었습니다."));
	}
}
