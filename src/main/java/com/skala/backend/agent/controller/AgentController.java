package com.skala.backend.agent.controller;

import com.skala.backend.agent.dto.AgentDtos.AgentLogResponse;
import com.skala.backend.agent.dto.AgentDtos.AgentRunRequest;
import com.skala.backend.agent.dto.AgentDtos.AgentRunResponse;
import com.skala.backend.agent.dto.OcrAgentDtos.OcrEvidenceMatchRequest;
import com.skala.backend.agent.dto.OcrAgentDtos.OcrUsageStatementParseRequest;
import com.skala.backend.agent.dto.OcrAgentDtos.OcrWorkflowResponse;
import com.skala.backend.agent.service.AgentLogService;
import com.skala.backend.agent.service.AgentService;
import com.skala.backend.agent.service.OcrAgentService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/projects/{projectId}/agents")
@Tag(name = "Agent", description = "agent_logs 조회(R-28) 및 FastAPI agent 호출 스켈레톤")
@SecurityRequirement(name = OpenApiConfig.COOKIE_AUTH)
public class AgentController {

	private final AgentService agentService;
	private final OcrAgentService ocrAgentService;
	private final AgentLogService agentLogService;

	public AgentController(AgentService agentService, OcrAgentService ocrAgentService, AgentLogService agentLogService) {
		this.agentService = agentService;
		this.ocrAgentService = ocrAgentService;
		this.agentLogService = agentLogService;
	}

	// R-28: agent_logs 조회 — 완전 구현
	@GetMapping("/logs")
	@Operation(summary = "agent_logs 조회 (R-28)", description = "runId 또는 usageStatementId 중 하나를 필수로 전달합니다.")
	public ResponseEntity<ApiResponse<List<AgentLogResponse>>> getLogs(
			@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser currentUser,
			@PathVariable Long projectId,
			@RequestParam(required = false) UUID runId,
			@RequestParam(required = false) Long usageStatementId
	) {
		List<AgentLogResponse> response = agentLogService.getLogs(currentUser.id(), projectId, runId, usageStatementId);
		return ResponseEntity.ok(ApiResponse.success(response, "agent 로그 조회에 성공했습니다."));
	}

	// 스켈레톤 — FastAPI 엔드포인트 확정 후 구현 예정 (현재 501 반환)
	@PostMapping("/{agentType}/run")
	@Operation(summary = "Agent 실행 (스켈레톤)", description = "FastAPI 엔드포인트 확정 후 구현 예정입니다.")
	public ResponseEntity<ApiResponse<AgentRunResponse>> runAgent(
			@Parameter(hidden = true)
			@AuthenticationPrincipal AuthenticatedUser currentUser,
			@PathVariable Long projectId,
			@PathVariable String agentType,
			@Valid @RequestBody AgentRunRequest request
	) {
		AgentRunResponse response = agentService.run(currentUser, projectId, agentType, request);
		return ResponseEntity.ok(ApiResponse.success(response, "Agent 실행 결과를 저장했습니다."));
	}

	// 스켈레톤 — FastAPI 엔드포인트 확정 후 구현 예정 (현재 501 반환)
	@PostMapping("/ocr/usage-statements/parse")
	@Operation(summary = "사용내역서 OCR 파싱 (스켈레톤)", description = "FastAPI 엔드포인트 확정 후 구현 예정입니다.")
	public ResponseEntity<ApiResponse<OcrWorkflowResponse>> parseUsageStatement(
			@Parameter(hidden = true)
			@AuthenticationPrincipal AuthenticatedUser currentUser,
			@PathVariable Long projectId,
			@Valid @RequestBody OcrUsageStatementParseRequest request
	) {
		OcrWorkflowResponse response = ocrAgentService.parseUsageStatement(currentUser, projectId, request);
		return ResponseEntity.ok(ApiResponse.success(response, "사용내역서 OCR 파싱 결과를 저장했습니다."));
	}

	// 스켈레톤 — FastAPI 엔드포인트 확정 후 구현 예정 (현재 501 반환)
	@PostMapping("/ocr/evidence/parse-and-match")
	@Operation(summary = "증빙 OCR 및 사용내역서 매칭 (스켈레톤)", description = "FastAPI 엔드포인트 확정 후 구현 예정입니다.")
	public ResponseEntity<ApiResponse<OcrWorkflowResponse>> parseAndMatchEvidence(
			@Parameter(hidden = true)
			@AuthenticationPrincipal AuthenticatedUser currentUser,
			@PathVariable Long projectId,
			@Valid @RequestBody OcrEvidenceMatchRequest request
	) {
		OcrWorkflowResponse response = ocrAgentService.parseAndMatchEvidence(currentUser, projectId, request);
		return ResponseEntity.ok(ApiResponse.success(response, "증빙 OCR 매칭 결과를 저장했습니다."));
	}
}
