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

@RestController
@RequestMapping("/projects/{projectId}/agents")
@Tag(name = "Agent", description = "agent_logs 조회 API")
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
	@Operation(
			summary = "agent_logs 조회",
			description = "Agent 실행 상태를 조회합니다. `statusCode`가 `pending`/`running`이면 처리 중, `success`/`fail`이면 완료입니다."
	)
	public ResponseEntity<ApiResponse<List<AgentResponses.LogResponse>>> getLogs(
			@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser currentUser,
			@PathVariable Long projectId,
			@Parameter(description = "사용내역서 ID")
			@RequestParam(required = false) Long usageStatementId
	) {
		List<AgentResponses.LogResponse> response = agentLogService.getLogs(currentUser.id(), projectId, usageStatementId);
		return ResponseEntity.ok(ApiResponse.success(response, "agent 로그 조회에 성공했습니다."));
	}

	@PostMapping("/parse")
	@Operation(
			tags = {"AI 실행"},
			summary = "사용내역서 분석 (OCR + classi)",
			description = """
					사용내역서 파일을 OCR로 읽어 세부항목을 추출하고 카테고리를 분류합니다.
					사용내역서 파일 업로드 완료 직후 호출합니다.
					실행 결과는 `GET /agents/logs`의 `statusCode`로 확인합니다.
					"""
	)
	public ResponseEntity<ApiResponse<Void>> parse(
			@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser currentUser,
			@PathVariable Long projectId,
			@Valid @RequestBody AgentRequests.ParseRequest request
	) {
		agentService.parse(currentUser.id(), projectId, request);
		return ResponseEntity.ok(ApiResponse.success(null, "사용내역서 분석 요청이 접수되었습니다."));
	}

	@PostMapping("/classify")
	@Operation(
			tags = {"AI 실행"},
			summary = "세부항목 분류 (classi)",
			description = """
					사용자가 입력한 세부항목 데이터를 classi agent에 전달해 카테고리를 분류하고 DB에 적재합니다.
					세부항목 추가 폼에서 "등록" 버튼 클릭 시 호출합니다.
					완료 후 프론트엔드는 사용내역서 상세를 재조회해 추가된 항목을 확인합니다.
					"""
	)
	public ResponseEntity<ApiResponse<Void>> classify(
			@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser currentUser,
			@PathVariable Long projectId,
			@Valid @RequestBody AgentRequests.ClassifyRequest request
	) {
		agentService.classify(currentUser.id(), projectId, request);
		return ResponseEntity.ok(ApiResponse.success(null, "항목 분류 요청이 접수되었습니다."));
	}

	@PostMapping("/validate")
	@Operation(
			tags = {"AI 실행"},
			summary = "유효성 검증 (link + vision + safety_docs)",
			description = """
					증빙 파일의 유효성을 검증합니다. link, vision, safety_docs agent가 동시에 실행됩니다.
					사용자가 "유효성 검증" 버튼을 클릭했을 때 호출합니다.
					실행 결과는 `GET /agents/logs`의 `statusCode`로 확인하고,
					완료 후 `GET /agents/warnings`에서 문제 항목을 확인합니다.
					"""
	)
	public ResponseEntity<ApiResponse<Void>> validate(
			@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser currentUser,
			@PathVariable Long projectId,
			@Valid @RequestBody AgentRequests.ValidateRequest request
	) {
		agentService.validate(currentUser.id(), projectId, request);
		return ResponseEntity.ok(ApiResponse.success(null, "유효성 검증 요청이 접수되었습니다."));
	}
}
