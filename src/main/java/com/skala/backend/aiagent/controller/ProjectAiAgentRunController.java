package com.skala.backend.aiagent.controller;

import com.skala.backend.aiagent.dto.AiAgentRunRequests.StartAiAgentRunRequest;
import com.skala.backend.aiagent.dto.AiAgentRunResponses.AiAgentRunResponse;
import com.skala.backend.aiagent.service.AiAgentRunService;
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
@RequestMapping("/projects/{projectId}/ai-agent-runs")
@Tag(name = "프로젝트 AI Agent", description = "프로젝트 AI Agent 실행 API")
@SecurityRequirement(name = OpenApiConfig.COOKIE_AUTH)
public class ProjectAiAgentRunController {

	private final AiAgentRunService aiAgentRunService;

	public ProjectAiAgentRunController(AiAgentRunService aiAgentRunService) {
		this.aiAgentRunService = aiAgentRunService;
	}

	@PostMapping
	@Operation(summary = "프로젝트 AI Agent 실행")
	public ResponseEntity<ApiResponse<AiAgentRunResponse>> run(
			@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser currentUser,
			@PathVariable Long projectId,
			@RequestBody @Valid StartAiAgentRunRequest request
	) {
		AiAgentRunResponse response = aiAgentRunService.run(currentUser.id(), projectId, request);
		return ResponseEntity.ok(ApiResponse.success(response, "AI Agent 실행을 완료했습니다."));
	}
}
