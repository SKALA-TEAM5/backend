package com.skala.backend.agent.controller;

import com.skala.backend.agent.dto.AgentDtos.AgentRunRequest;
import com.skala.backend.agent.dto.AgentDtos.AgentRunResponse;
import com.skala.backend.agent.dto.OcrAgentDtos.OcrEvidenceMatchRequest;
import com.skala.backend.agent.dto.OcrAgentDtos.OcrUsageStatementParseRequest;
import com.skala.backend.agent.dto.OcrAgentDtos.OcrWorkflowResponse;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/projects/{projectId}/agents")
@Tag(name = "Agent", description = "Spring에서 FastAPI agent를 호출하고 validation_logs에 결과를 저장하는 API")
@SecurityRequirement(name = OpenApiConfig.COOKIE_AUTH)
public class AgentController {

	private final AgentService agentService;
	private final OcrAgentService ocrAgentService;

	public AgentController(AgentService agentService, OcrAgentService ocrAgentService) {
		this.agentService = agentService;
		this.ocrAgentService = ocrAgentService;
	}

	@PostMapping("/{agentType}/run")
	@Operation(
			summary = "Agent 실행",
			description = "프로젝트/사용내역서 context를 조립해 FastAPI agent를 호출하고 결과를 validation_logs에 저장합니다."
	)
	public ResponseEntity<ApiResponse<AgentRunResponse>> runAgent(
			@Parameter(hidden = true)
			@AuthenticationPrincipal AuthenticatedUser currentUser,
			@Parameter(description = "프로젝트 ID", example = "1")
			@PathVariable Long projectId,
			@Parameter(description = "Agent 유형. validator, classifier, safety_doc, report", example = "validator")
			@PathVariable String agentType,
			@Valid @RequestBody AgentRunRequest request
	) {
		AgentRunResponse response = agentService.run(currentUser, projectId, agentType, request);
		return ResponseEntity.ok(ApiResponse.success(response, "Agent 실행 결과를 저장했습니다."));
	}

	// 사용내역서 업로드 플로우의 시작점입니다.
	// FastAPI OCR로 PDF를 구조화하고, 성공하면 DB 적재 후 classification까지 이어집니다.
	@PostMapping("/ocr/usage-statements/parse")
	@Operation(
			summary = "사용내역서 OCR 파싱",
			description = "사용내역서 파일을 FastAPI OCR로 파싱하고 결과를 validation_logs에 저장합니다."
	)
	public ResponseEntity<ApiResponse<OcrWorkflowResponse>> parseUsageStatement(
			@Parameter(hidden = true)
			@AuthenticationPrincipal AuthenticatedUser currentUser,
			@Parameter(description = "프로젝트 ID", example = "1")
			@PathVariable Long projectId,
			@Valid @RequestBody OcrUsageStatementParseRequest request
	) {
		OcrWorkflowResponse response = ocrAgentService.parseUsageStatement(currentUser, projectId, request);
		return ResponseEntity.ok(ApiResponse.success(response, "사용내역서 OCR 파싱 결과를 저장했습니다."));
	}

	// 이미 DB에 적재된 사용내역서 항목을 기준으로, 업로드된 증빙 파일을 OCR 후 매칭합니다.
	// matched인 경우에만 evidence_file_links를 생성해 업로드 완료 상태로 봅니다.
	@PostMapping("/ocr/evidence/parse-and-match")
	@Operation(
			summary = "증빙 OCR 및 사용내역서 매칭",
			description = "증빙 파일 OCR 결과를 사용내역서 상세항목과 매칭하고, matched이면 증빙 연결을 생성합니다."
	)
	public ResponseEntity<ApiResponse<OcrWorkflowResponse>> parseAndMatchEvidence(
			@Parameter(hidden = true)
			@AuthenticationPrincipal AuthenticatedUser currentUser,
			@Parameter(description = "프로젝트 ID", example = "1")
			@PathVariable Long projectId,
			@Valid @RequestBody OcrEvidenceMatchRequest request
	) {
		OcrWorkflowResponse response = ocrAgentService.parseAndMatchEvidence(currentUser, projectId, request);
		return ResponseEntity.ok(ApiResponse.success(response, "증빙 OCR 매칭 결과를 저장했습니다."));
	}
}
