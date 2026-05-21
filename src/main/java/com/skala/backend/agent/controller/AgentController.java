package com.skala.backend.agent.controller;

import com.skala.backend.agent.dto.AgentRequests;
import com.skala.backend.agent.dto.AgentResponses;
import com.skala.backend.agent.service.AgentLogService;
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
@Tag(name = "Agent", description = "agent_logs 조회 및 FastAPI agent 호출 스켈레톤")
@SecurityRequirement(name = OpenApiConfig.COOKIE_AUTH)
public class AgentController {

	private final AgentService agentService;
	private final AgentLogService agentLogService;

	public AgentController(AgentService agentService, AgentLogService agentLogService) {
		this.agentService = agentService;
		this.agentLogService = agentLogService;
	}

	@GetMapping("/warnings")
	@Operation(
			tags = {"에이전트 경고"},
			summary = "에이전트 경고 목록 조회",
			description = """
					검증 에이전트가 발견한 문제 항목 목록을 반환합니다.

					**경고로 판단하는 기준**
					- `usage_statement_item_id IS NOT NULL` — 특정 상세항목에서 문제를 발견한 경우
					- `status_code = 'failed'` — 에이전트 실행 자체가 실패한 경우 (이때 `itemId`는 null일 수 있음)

					**agentTypeCode별 의미**
					- `classi`     : 항목 분류 실패
					- `safety-doc` : 필요 서류 미충족 → 어떤 서류가 없는지는 `/evidence-requirements` 추가 조회
					- `link`       : 파일-항목 금액·날짜 불일치
					- `vision`     : 파일 판독 불가 또는 안전시설 미확인
					- `legal`      : 카테고리 법령 위반 (itemId = null)

					**details 필드**
					JSON 문자열입니다. `reason` 키에 사람이 읽을 수 있는 사유가 담겨 있습니다.
					예: `{"reason": "영수증 금액(50,000원)이 항목 금액(80,000원)과 불일치"}`

					**runId 활용**
					같은 배치 실행의 전체 로그를 보려면 `GET /agents/logs?runId={runId}` 를 호출하세요.

					**필터링**
					`usageStatementId` 파라미터를 전달하면 해당 사용내역서의 경고만 반환합니다.
					생략하면 프로젝트 전체 경고를 반환합니다.
					"""
	)
	public ResponseEntity<ApiResponse<List<AgentResponses.WarningResponse>>> getWarnings(
			@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser currentUser,
			@PathVariable Long projectId,
			@Parameter(description = "사용내역서 ID (생략 시 프로젝트 전체 경고 반환)")
			@RequestParam(required = false) Long usageStatementId
	) {
		List<AgentResponses.WarningResponse> response = agentLogService.getWarnings(currentUser.id(), projectId, usageStatementId);
		return ResponseEntity.ok(ApiResponse.success(response, "에이전트 경고 목록 조회에 성공했습니다."));
	}

	@GetMapping("/logs")
	@Operation(summary = "agent_logs 조회", description = "runId 또는 usageStatementId 중 하나를 필수로 전달합니다.")
	public ResponseEntity<ApiResponse<List<AgentResponses.LogResponse>>> getLogs(
			@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser currentUser,
			@PathVariable Long projectId,
			@RequestParam(required = false) UUID runId,
			@RequestParam(required = false) Long usageStatementId
	) {
		List<AgentResponses.LogResponse> response = agentLogService.getLogs(currentUser.id(), projectId, runId, usageStatementId);
		return ResponseEntity.ok(ApiResponse.success(response, "agent 로그 조회에 성공했습니다."));
	}

	// 스켈레톤 — FastAPI 엔드포인트 확정 후 구현 예정 (현재 501 반환)
	@PostMapping("/{agentType}/run")
	@Operation(summary = "Agent 실행 (스켈레톤)", description = "FastAPI 엔드포인트 확정 후 구현 예정입니다.")
	public ResponseEntity<ApiResponse<AgentResponses.RunResponse>> runAgent(
			@Parameter(hidden = true)
			@AuthenticationPrincipal AuthenticatedUser currentUser,
			@PathVariable Long projectId,
			@PathVariable String agentType,
			@Valid @RequestBody AgentRequests.RunRequest request
	) {
		AgentResponses.RunResponse response = agentService.run(currentUser, projectId, agentType, request);
		return ResponseEntity.ok(ApiResponse.success(response, "Agent 실행 결과를 저장했습니다."));
	}
}
