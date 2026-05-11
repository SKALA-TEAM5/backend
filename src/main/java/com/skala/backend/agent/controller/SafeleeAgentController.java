package com.skala.backend.agent.controller;

import com.skala.backend.agent.dto.SafeleeAgentDtos.EvidenceRequirementInputResponse;
import com.skala.backend.agent.dto.SafeleeAgentDtos.EvidenceRequirementJudgementRequest;
import com.skala.backend.agent.dto.SafeleeAgentDtos.EvidenceRequirementJudgementResponse;
import com.skala.backend.agent.dto.SafeleeAgentDtos.EvidenceRequirementListResponse;
import com.skala.backend.agent.service.SafeleeAgentService;
import com.skala.backend.auth.security.AuthenticatedUser;
import com.skala.backend.global.config.OpenApiConfig;
import com.skala.backend.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/projects/{projectId}/usage-statements/{statementId}/line-items/{itemId}")
@Tag(name = "SafeLee Agent", description = "AI 필수 증빙 판단 입력/저장/조회 API")
@SecurityRequirement(name = OpenApiConfig.COOKIE_AUTH)
public class SafeleeAgentController {

	private final SafeleeAgentService safeleeAgentService;

	public SafeleeAgentController(SafeleeAgentService safeleeAgentService) {
		this.safeleeAgentService = safeleeAgentService;
	}

	@GetMapping("/evidence-requirement-input")
	@Operation(summary = "AI 필수 증빙 판단 입력 조회")
	public ResponseEntity<ApiResponse<EvidenceRequirementInputResponse>> getInput(
			@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser currentUser,
			@PathVariable Long projectId,
			@PathVariable Long statementId,
			@PathVariable Long itemId
	) {
		EvidenceRequirementInputResponse response = safeleeAgentService.getInput(currentUser, projectId, statementId, itemId);
		return ResponseEntity.ok(ApiResponse.success(response, "필수 증빙 판단 입력 조회에 성공했습니다."));
	}

	@PostMapping("/evidence-requirements/judgement")
	@Operation(summary = "AI 필수 증빙 판단 결과 저장")
	public ResponseEntity<ApiResponse<EvidenceRequirementJudgementResponse>> saveJudgement(
			@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser currentUser,
			@PathVariable Long projectId,
			@PathVariable Long statementId,
			@PathVariable Long itemId,
			@Valid @RequestBody EvidenceRequirementJudgementRequest request
	) {
		EvidenceRequirementJudgementResponse response = safeleeAgentService.saveJudgement(currentUser, projectId, statementId, itemId, request);
		return ResponseEntity.ok(ApiResponse.success(response, "필수 증빙 판단 결과 저장에 성공했습니다."));
	}

	@GetMapping("/evidence-requirements")
	@Operation(summary = "저장된 필수 증빙 조회")
	public ResponseEntity<ApiResponse<EvidenceRequirementListResponse>> listRequirements(
			@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser currentUser,
			@PathVariable Long projectId,
			@PathVariable Long statementId,
			@PathVariable Long itemId
	) {
		EvidenceRequirementListResponse response = safeleeAgentService.listRequirements(currentUser, projectId, statementId, itemId);
		return ResponseEntity.ok(ApiResponse.success(response, "필수 증빙 조회에 성공했습니다."));
	}
}
