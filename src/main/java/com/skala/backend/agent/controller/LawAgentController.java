package com.skala.backend.agent.controller;

import com.skala.backend.agent.dto.LawAgentDtos.ClassificationRunRequest;
import com.skala.backend.agent.dto.LawAgentDtos.LawAgentRunResponse;
import com.skala.backend.agent.dto.LawAgentDtos.ValidationConfirmRequest;
import com.skala.backend.agent.dto.LawAgentDtos.ValidationRunRequest;
import com.skala.backend.agent.service.LawAgentService;
import com.skala.backend.auth.security.AuthenticatedUser;
import com.skala.backend.global.config.OpenApiConfig;
import com.skala.backend.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/projects/{projectId}")
@Tag(name = "Law Agent", description = "Classifier / Validator agent API")
@SecurityRequirement(name = OpenApiConfig.COOKIE_AUTH)
public class LawAgentController {

	private final LawAgentService lawAgentService;

	public LawAgentController(LawAgentService lawAgentService) {
		this.lawAgentService = lawAgentService;
	}

	@PostMapping("/usage-statements/{statementId}/classification")
	@Operation(summary = "사용내역서 분류 agent 실행")
	public ResponseEntity<ApiResponse<LawAgentRunResponse>> runClassification(
			@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser currentUser,
			@PathVariable Long projectId,
			@PathVariable Long statementId,
			@RequestBody(required = false) ClassificationRunRequest request
	) {
		LawAgentRunResponse response = lawAgentService.runClassification(currentUser, projectId, statementId, request);
		return ResponseEntity.ok(ApiResponse.success(response, "분류 agent 실행 결과를 저장했습니다."));
	}

	@GetMapping("/usage-statements/{statementId}/classification/{classificationId}")
	@Operation(summary = "분류 agent 작업 상태 조회")
	public ResponseEntity<ApiResponse<Map<String, Object>>> getClassificationStatus(
			@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser currentUser,
			@PathVariable Long projectId,
			@PathVariable Long statementId,
			@PathVariable String classificationId
	) {
		Map<String, Object> response = lawAgentService.getClassificationStatus(currentUser, projectId, statementId, classificationId);
		return ResponseEntity.ok(ApiResponse.success(response, "분류 agent 상태 조회에 성공했습니다."));
	}

	@GetMapping("/usage-statements/{statementId}/classification/latest")
	@Operation(summary = "최신 분류 결과 조회")
	public ResponseEntity<ApiResponse<Map<String, Object>>> getLatestClassification(
			@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser currentUser,
			@PathVariable Long projectId,
			@PathVariable Long statementId
	) {
		Map<String, Object> response = lawAgentService.getLatestClassification(currentUser, projectId, statementId);
		return ResponseEntity.ok(ApiResponse.success(response, "최신 분류 결과 조회에 성공했습니다."));
	}

	@PostMapping("/validations")
	@Operation(summary = "법령 검증 agent 실행")
	public ResponseEntity<ApiResponse<LawAgentRunResponse>> runValidation(
			@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser currentUser,
			@PathVariable Long projectId,
			@RequestBody ValidationRunRequest request
	) {
		LawAgentRunResponse response = lawAgentService.runValidation(currentUser, projectId, request);
		return ResponseEntity.ok(ApiResponse.success(response, "검증 agent 실행 결과를 저장했습니다."));
	}

	@GetMapping("/validations/{validationId}")
	@Operation(summary = "검증 agent 작업 상태 조회")
	public ResponseEntity<ApiResponse<Map<String, Object>>> getValidationStatus(
			@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser currentUser,
			@PathVariable Long projectId,
			@PathVariable String validationId
	) {
		Map<String, Object> response = lawAgentService.getValidationStatus(currentUser, projectId, validationId);
		return ResponseEntity.ok(ApiResponse.success(response, "검증 agent 상태 조회에 성공했습니다."));
	}

	@GetMapping("/validations/latest")
	@Operation(summary = "최신 검증 결과 조회")
	public ResponseEntity<ApiResponse<Map<String, Object>>> getLatestValidation(
			@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser currentUser,
			@PathVariable Long projectId
	) {
		Map<String, Object> response = lawAgentService.getLatestValidation(currentUser, projectId);
		return ResponseEntity.ok(ApiResponse.success(response, "최신 검증 결과 조회에 성공했습니다."));
	}

	@PostMapping("/validations/{validationId}/confirm")
	@Operation(summary = "검증 결과 확인")
	public ResponseEntity<ApiResponse<Map<String, Object>>> confirmValidation(
			@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser currentUser,
			@PathVariable Long projectId,
			@PathVariable String validationId,
			@RequestBody ValidationConfirmRequest request
	) {
		Map<String, Object> response = lawAgentService.confirmValidation(currentUser, projectId, validationId, request);
		return ResponseEntity.ok(ApiResponse.success(response, "검증 결과 확인에 성공했습니다."));
	}
}
