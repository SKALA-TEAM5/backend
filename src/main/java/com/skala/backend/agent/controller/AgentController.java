package com.skala.backend.agent.controller;

import com.skala.backend.agent.dto.AgentDtos.AgentRunRequest;
import com.skala.backend.agent.dto.AgentDtos.AgentRunResponse;
import com.skala.backend.agent.service.AgentService;
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

	public AgentController(AgentService agentService) {
		this.agentService = agentService;
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
}
