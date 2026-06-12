package com.skala.backend.agent.controller;

import com.skala.backend.agent.dto.UsageRecordResponses;
import com.skala.backend.agent.service.AgentUsageRecordService;
import com.skala.backend.auth.security.AuthenticatedUser;
import com.skala.backend.global.config.OpenApiConfig;
import com.skala.backend.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/usage-records")
@Tag(name = "AI 사용량", description = "에이전트 토큰 사용량 집계 조회 API")
@SecurityRequirement(name = OpenApiConfig.COOKIE_AUTH)
public class AgentUsageRecordController {

	private final AgentUsageRecordService service;

	public AgentUsageRecordController(AgentUsageRecordService service) {
		this.service = service;
	}

	@GetMapping("/by-user")
	@Operation(
			summary = "사용자별 토큰 사용량",
			description = """
					사용자 단위로 토큰 사용량과 비용을 집계합니다.

					**접근 범위** (대시보드와 동일 정책)
					- `system_admin`: 전체 집계
					- `admin` / `user`: 본인이 배정된 프로젝트의 사용량만 집계
					- `agent`: 403

					**정렬**: 비용(costUsd) 내림차순
					"""
	)
	public ResponseEntity<ApiResponse<List<UsageRecordResponses.ByUser>>> byUser(
			@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser currentUser,
			@Parameter(description = "사용자 ID 필터 (집계 범위 내에서 적용)")
			@RequestParam(required = false) Long userId,
			@Parameter(description = "프로젝트 ID 필터")
			@RequestParam(required = false) Long projectId,
			@Parameter(description = "에이전트 유형 필터", example = "safety-doc")
			@RequestParam(required = false) String agentTypeCode,
			@Parameter(description = "시작 날짜 (YYYY-MM-DD, 포함)")
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@Parameter(description = "종료 날짜 (YYYY-MM-DD, 포함)")
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
	) {
		List<UsageRecordResponses.ByUser> result = service.getByUser(
				currentUser.id(), currentUser.roleCode(), userId, projectId, agentTypeCode, from, to);
		return ResponseEntity.ok(ApiResponse.success(result, "사용자별 사용량 조회에 성공했습니다."));
	}

	@GetMapping("/by-project")
	@Operation(
			summary = "프로젝트별 토큰 사용량",
			description = """
					프로젝트 단위로 토큰 사용량과 비용을 집계합니다.

					**접근 범위** (대시보드와 동일 정책)
					- `system_admin`: 전체 집계
					- `admin` / `user`: 본인이 배정된 프로젝트의 사용량만 집계
					- `agent`: 403

					**정렬**: 비용(costUsd) 내림차순
					"""
	)
	public ResponseEntity<ApiResponse<List<UsageRecordResponses.ByProject>>> byProject(
			@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser currentUser,
			@Parameter(description = "사용자 ID 필터 (집계 범위 내에서 적용)")
			@RequestParam(required = false) Long userId,
			@Parameter(description = "프로젝트 ID 필터")
			@RequestParam(required = false) Long projectId,
			@Parameter(description = "에이전트 유형 필터", example = "legal")
			@RequestParam(required = false) String agentTypeCode,
			@Parameter(description = "시작 날짜 (YYYY-MM-DD, 포함)")
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@Parameter(description = "종료 날짜 (YYYY-MM-DD, 포함)")
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
	) {
		List<UsageRecordResponses.ByProject> result = service.getByProject(
				currentUser.id(), currentUser.roleCode(), userId, projectId, agentTypeCode, from, to);
		return ResponseEntity.ok(ApiResponse.success(result, "프로젝트별 사용량 조회에 성공했습니다."));
	}

	@GetMapping("/by-agent")
	@Operation(
			summary = "에이전트별 토큰 사용량",
			description = """
					에이전트 유형 단위로 토큰 사용량과 비용을 집계합니다.

					**에이전트 유형**: `classi` / `safety-doc` / `link` / `vision` / `legal` / `report` / `orchestrator`

					**접근 범위** (대시보드와 동일 정책)
					- `system_admin`: 전체 집계
					- `admin` / `user`: 본인이 배정된 프로젝트의 사용량만 집계
					- `agent`: 403

					**정렬**: 비용(costUsd) 내림차순
					"""
	)
	public ResponseEntity<ApiResponse<List<UsageRecordResponses.ByAgent>>> byAgent(
			@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser currentUser,
			@Parameter(description = "사용자 ID 필터 (집계 범위 내에서 적용)")
			@RequestParam(required = false) Long userId,
			@Parameter(description = "프로젝트 ID 필터")
			@RequestParam(required = false) Long projectId,
			@Parameter(description = "에이전트 유형 필터", example = "vision")
			@RequestParam(required = false) String agentTypeCode,
			@Parameter(description = "시작 날짜 (YYYY-MM-DD, 포함)")
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@Parameter(description = "종료 날짜 (YYYY-MM-DD, 포함)")
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
	) {
		List<UsageRecordResponses.ByAgent> result = service.getByAgent(
				currentUser.id(), currentUser.roleCode(), userId, projectId, agentTypeCode, from, to);
		return ResponseEntity.ok(ApiResponse.success(result, "에이전트별 사용량 조회에 성공했습니다."));
	}

	@GetMapping("/by-month")
	@Operation(
			summary = "월별 토큰 사용량",
			description = """
					월(YYYY-MM) 단위로 토큰 사용량과 비용을 집계합니다. 날짜는 UTC 기준입니다.

					**접근 범위** (대시보드와 동일 정책)
					- `system_admin`: 전체 집계
					- `admin` / `user`: 본인이 배정된 프로젝트의 사용량만 집계
					- `agent`: 403

					**정렬**: 연월 오름차순
					"""
	)
	public ResponseEntity<ApiResponse<List<UsageRecordResponses.ByMonth>>> byMonth(
			@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser currentUser,
			@Parameter(description = "사용자 ID 필터 (집계 범위 내에서 적용)")
			@RequestParam(required = false) Long userId,
			@Parameter(description = "프로젝트 ID 필터")
			@RequestParam(required = false) Long projectId,
			@Parameter(description = "에이전트 유형 필터", example = "legal")
			@RequestParam(required = false) String agentTypeCode,
			@Parameter(description = "시작 날짜 (YYYY-MM-DD, 포함)")
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@Parameter(description = "종료 날짜 (YYYY-MM-DD, 포함)")
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
	) {
		List<UsageRecordResponses.ByMonth> result = service.getByMonth(
				currentUser.id(), currentUser.roleCode(), userId, projectId, agentTypeCode, from, to);
		return ResponseEntity.ok(ApiResponse.success(result, "월별 사용량 조회에 성공했습니다."));
	}

	@GetMapping("/by-date")
	@Operation(
			summary = "일별 토큰 사용량",
			description = """
					날짜 단위로 토큰 사용량과 비용을 집계합니다. 날짜는 UTC 기준입니다.

					**접근 범위** (대시보드와 동일 정책)
					- `system_admin`: 전체 집계
					- `admin` / `user`: 본인이 배정된 프로젝트의 사용량만 집계
					- `agent`: 403

					**정렬**: 날짜 오름차순
					"""
	)
	public ResponseEntity<ApiResponse<List<UsageRecordResponses.ByDate>>> byDate(
			@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser currentUser,
			@Parameter(description = "사용자 ID 필터 (집계 범위 내에서 적용)")
			@RequestParam(required = false) Long userId,
			@Parameter(description = "프로젝트 ID 필터")
			@RequestParam(required = false) Long projectId,
			@Parameter(description = "에이전트 유형 필터", example = "report")
			@RequestParam(required = false) String agentTypeCode,
			@Parameter(description = "시작 날짜 (YYYY-MM-DD, 포함)")
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@Parameter(description = "종료 날짜 (YYYY-MM-DD, 포함)")
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
	) {
		List<UsageRecordResponses.ByDate> result = service.getByDate(
				currentUser.id(), currentUser.roleCode(), userId, projectId, agentTypeCode, from, to);
		return ResponseEntity.ok(ApiResponse.success(result, "일별 사용량 조회에 성공했습니다."));
	}
}
