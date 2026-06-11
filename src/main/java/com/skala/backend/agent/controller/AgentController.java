package com.skala.backend.agent.controller;

import com.skala.backend.agent.dto.AgentRequests;
import com.skala.backend.agent.dto.AgentResponses;
import com.skala.backend.agent.service.AgentLogService;
import com.skala.backend.agent.service.AgentService;
import com.skala.backend.agent.service.TodoService;
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
import org.springframework.web.bind.annotation.PatchMapping;
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
	private final TodoService todoService;

	public AgentController(AgentService agentService, AgentLogService agentLogService, TodoService todoService) {
		this.agentService = agentService;
		this.agentLogService = agentLogService;
		this.todoService = todoService;
	}

	@GetMapping("/todos")
	@Operation(
			summary = "TODO 목록 조회",
			description = """
					사용내역서의 TODO를 평탄화된 단일 리스트로 반환합니다. (todos 읽기 모델)

					**구성**
					- agent 실행(validate / legal) 직후 Spring이 `agent_logs.details` JSONB의 todos[]를
					  평탄화하여 todos 테이블에 재생성합니다. 본 조회는 그 테이블만 읽습니다.
					- 포함 대상: `result_code = 'hil'` 또는 `status_code = 'fail'` 인 safety-doc / link / vision / legal.
					- 사용내역서 상태가 `review_completed`이면 legal TODO는 제외됩니다.
					- 각 TODO의 `confirmed`는 사용자가 확인(체크)한 상태이며 agent 재실행에도 보존됩니다
					  (단, 해당 TODO의 reason이 바뀌면 자동 해제).
					"""
	)
	public ResponseEntity<ApiResponse<List<AgentResponses.TodoResponse>>> getTodos(
			@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser currentUser,
			@PathVariable Long projectId,
			@Parameter(description = "사용내역서 ID", required = true)
			@RequestParam Long usageStatementId
	) {
		List<AgentResponses.TodoResponse> response =
				todoService.getTodos(currentUser.id(), projectId, usageStatementId);
		return ResponseEntity.ok(ApiResponse.success(response, "TODO 목록 조회에 성공했습니다."));
	}

	@PatchMapping("/todos/{todoId}/confirm")
	@Operation(
			summary = "TODO 확인(체크) 토글",
			description = """
					TODO 단건의 확인(체크) 상태를 설정/해제합니다.

					- 조회 응답의 `todoId`를 경로에 그대로 사용합니다.
					- `confirmed=true`면 확인, `false`면 확인 해제입니다.
					- agent 재실행으로 해당 TODO의 reason이 바뀌면 새 TODO로 재생성되어 확인 상태는 자동 해제됩니다
					  (내용이 동일하게 재생성되면 유지).
					"""
	)
	public ResponseEntity<ApiResponse<Void>> confirmTodo(
			@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser currentUser,
			@PathVariable Long projectId,
			@Parameter(description = "TODO ID", required = true) @PathVariable Long todoId,
			@Valid @RequestBody AgentRequests.ConfirmTodoRequest request
	) {
		todoService.confirmTodo(currentUser.id(), projectId, todoId, request.confirmed());
		return ResponseEntity.ok(ApiResponse.success(null, "TODO 확인 상태가 변경되었습니다."));
	}

	@GetMapping("/button-states")
	@Operation(
			summary = "AI 버튼 활성화 상태 조회",
			description = """
					사용내역서 기준 validate / legal / report 버튼의 활성화 여부를 반환합니다.

					**활성화 규칙**
					- `validate` : `classi` 로그가 `status=success AND result_code=success`일 때 활성화.
					  vision / link / safety-doc 중 하나라도 running/pending이면 비활성화.
					- `legal`    : `safety-doc` 로그가 `status=success AND result_code IN (success, hil)`일 때 활성화.
					  `link` / `vision` 로그가 존재하는 경우 동일 조건을 충족해야 함 (없으면 무시).
					  legal이 running/pending이면 비활성화.
					- `report`   : `legal` 로그가 `status=success AND result_code IN (success, hil)`일 때 활성화.
					  report가 running/pending이면 비활성화.

					`enabled=false`이면 `reason` 필드에 사유가 담깁니다.
					"""
	)
	public ResponseEntity<ApiResponse<AgentResponses.ButtonStatesResponse>> getButtonStates(
			@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser currentUser,
			@PathVariable Long projectId,
			@Parameter(description = "사용내역서 ID", required = true)
			@RequestParam Long usageStatementId
	) {
		AgentResponses.ButtonStatesResponse response =
				agentService.getButtonStates(currentUser.id(), projectId, usageStatementId);
		return ResponseEntity.ok(ApiResponse.success(response, "버튼 상태 조회에 성공했습니다."));
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
					동기 호출 — 완료까지 최대 60s 대기 후 usageStatementId와 itemCount를 반환합니다.
					"""
	)
	public ResponseEntity<ApiResponse<AgentResponses.ParseResult>> parse(
			@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser currentUser,
			@PathVariable Long projectId,
			@Valid @RequestBody AgentRequests.ParseRequest request
	) {
		AgentResponses.ParseResult result = agentService.parse(currentUser.id(), projectId, request);
		return ResponseEntity.ok(ApiResponse.success(result, "사용내역서 분석이 완료되었습니다."));
	}

	@PostMapping("/validate")
	@Operation(
			tags = {"AI 실행"},
			summary = "유효성 검증 (link + vision + safety-doc)",
			description = "증빙 파일의 유효성을 검증합니다. link, vision, safety-doc agent가 비동기로 실행됩니다. 202 즉시 반환 — 진행 상태는 button-states 폴링으로 확인하세요."
	)
	public ResponseEntity<ApiResponse<Void>> validate(
			@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser currentUser,
			@PathVariable Long projectId,
			@Valid @RequestBody AgentRequests.ValidateRequest request
	) {
		agentService.validate(currentUser.id(), projectId, request);
		return ResponseEntity.accepted().body(ApiResponse.success(null, "유효성 검증이 시작되었습니다."));
	}

	@PostMapping("/legal")
	@Operation(
			tags = {"AI 실행"},
			summary = "법령 검증 (legal)",
			description = """
					사용내역서 항목의 법령 적합성을 검증합니다. 202 즉시 반환 — 진행 상태는 button-states 폴링으로 확인하세요.

					**실행 선행 조건 (미충족 시 400)**
					- `safety-doc` 로그가 `status=success AND result_code IN (success, hil)` 이어야 함
					- `link` 로그가 존재하는 경우 동일 조건 충족 필요
					- `vision` 로그가 존재하는 경우 동일 조건 충족 필요
					- legal이 실행 중(3분 이내)이면 409
					"""
	)
	public ResponseEntity<ApiResponse<Void>> legal(
			@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser currentUser,
			@PathVariable Long projectId,
			@Valid @RequestBody AgentRequests.LegalRequest request
	) {
		agentService.legal(currentUser.id(), projectId, request);
		return ResponseEntity.accepted().body(ApiResponse.success(null, "법령 검증이 시작되었습니다."));
	}

	@PostMapping("/report")
	@Operation(
			tags = {"AI 실행"},
			summary = "보고서 생성 (report)",
			description = """
					사용내역서 기반 보고서를 생성합니다. 202 즉시 반환 — 진행 상태는 button-states 폴링으로 확인하세요.

					**실행 선행 조건 (미충족 시 400)**
					- `legal` 로그가 `status=success AND result_code IN (success, hil)` 이어야 함
					- report가 실행 중(3분 이내)이면 409
					"""
	)
	public ResponseEntity<ApiResponse<Void>> report(
			@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser currentUser,
			@PathVariable Long projectId,
			@Valid @RequestBody AgentRequests.ReportRequest request
	) {
		agentService.report(currentUser.id(), projectId, request);
		return ResponseEntity.accepted().body(ApiResponse.success(null, "보고서 생성이 시작되었습니다."));
	}

	@GetMapping("/legal")
	@Operation(
			tags = {"AI 실행"},
			summary = "법령 검증 상세 조회",
			description = "agent_logs에 저장된 최신 법령 검증 데이터(details JSONB)를 반환합니다. 로그가 없으면 404를 반환합니다."
	)
	public ResponseEntity<ApiResponse<AgentResponses.LegalDetailResponse>> getLegalDetail(
			@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser currentUser,
			@PathVariable Long projectId,
			@Parameter(description = "사용내역서 ID", required = true)
			@RequestParam Long usageStatementId
	) {
		AgentResponses.LegalDetailResponse result = agentLogService.getLegalDetail(currentUser.id(), projectId, usageStatementId);
		return ResponseEntity.ok(ApiResponse.success(result, "법령 검증 조회에 성공했습니다."));
	}

	@GetMapping("/report")
	@Operation(
			tags = {"AI 실행"},
			summary = "보고서 상세 조회",
			description = "agent_logs에 저장된 최신 보고서 데이터(details JSONB)를 반환합니다."
	)
	public ResponseEntity<ApiResponse<AgentResponses.ReportDetailResponse>> getReportDetail(
			@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser currentUser,
			@PathVariable Long projectId,
			@Parameter(description = "사용내역서 ID", required = true)
			@RequestParam Long usageStatementId
	) {
		AgentResponses.ReportDetailResponse result = agentLogService.getReportDetail(currentUser.id(), projectId, usageStatementId);
		return ResponseEntity.ok(ApiResponse.success(result, "보고서 조회에 성공했습니다."));
	}
}
