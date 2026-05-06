package com.skala.backend.project.controller;

import com.skala.backend.auth.security.AuthenticatedUser;
import com.skala.backend.global.config.OpenApiConfig;
import com.skala.backend.global.response.ApiResponse;
import com.skala.backend.project.domain.ProjectStatusCode;
import com.skala.backend.project.dto.ProjectAssigneeListResponse;
import com.skala.backend.project.dto.ProjectCreateRequest;
import com.skala.backend.project.dto.ProjectDetailDataResponse;
import com.skala.backend.project.dto.ProjectListResponse;
import com.skala.backend.project.dto.ProjectUpdateRequest;
import com.skala.backend.project.dto.ReplaceProjectAssigneesRequest;
import com.skala.backend.project.service.ProjectService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/projects")
@Tag(name = "프로젝트", description = "프로젝트 목록, 상세, 생성, 수정, 삭제 API")
@SecurityRequirement(name = OpenApiConfig.COOKIE_AUTH)
public class ProjectController {

	private final ProjectService projectService;

	public ProjectController(ProjectService projectService) {
		this.projectService = projectService;
	}

	@GetMapping
	@Operation(
			summary = "프로젝트 목록 조회",
			description = """
					프로젝트 카드 목록을 조회합니다.
					- admin: 기본값 `scope=all`로 전체 프로젝트를 조회하며, `scope=assigned`로 본인이 담당자로 배정된 프로젝트만 조회할 수 있습니다.
					- user: 기본값과 허용값이 `scope=assigned`이며, 본인이 담당자로 배정된 프로젝트만 조회합니다.
					- system_admin: 프로젝트 업무 API를 사용할 수 없습니다.
					`assigneeUserId`는 admin의 특정 담당자 필터용이며, `scope=assigned`는 현재 로그인 사용자 기준 필터입니다.
					"""
	)
	public ResponseEntity<ApiResponse<ProjectListResponse>> listProjects(
			@Parameter(hidden = true)
			@AuthenticationPrincipal AuthenticatedUser currentUser,
			@Parameter(
					description = "조회 범위입니다. `all`은 전체 프로젝트, `assigned`는 현재 로그인 사용자가 담당자로 배정된 프로젝트만 조회합니다. admin 기본값은 all, user 기본값은 assigned입니다.",
					example = "all",
					schema = @Schema(allowableValues = {"all", "assigned"})
			)
			@RequestParam(required = false) String scope,
			@Parameter(description = "통합 검색어입니다. 프로젝트명, 계약번호 등 목록 검색에 사용합니다.", example = "안전")
			@RequestParam(required = false) String keyword,
			@Parameter(description = "프로젝트명 검색어입니다.", example = "스마트 안전관리")
			@RequestParam(required = false) String projectName,
			@Parameter(description = "계약번호 검색어입니다.", example = "CN-2026-001")
			@RequestParam(required = false) String contractNo,
			@Parameter(description = "담당자 사용자 ID입니다.", example = "3")
			@RequestParam(required = false) Long assigneeUserId,
			@Parameter(
					description = "프로젝트 상태입니다.",
					example = "active",
					schema = @Schema(allowableValues = {"active", "completed", "suspended"})
			)
			@RequestParam(required = false) ProjectStatusCode status,
			@Parameter(description = "공사 시작일 검색 시작값입니다.", example = "2026-01-01")
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodFrom,
			@Parameter(description = "공사 시작일 검색 종료값입니다.", example = "2026-12-31")
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodTo,
			@Parameter(
					description = "정렬 기준입니다.",
					example = "default",
					schema = @Schema(allowableValues = {
							"default",
							"project_name_asc",
							"project_name_desc",
							"progress_rate_desc",
							"progress_rate_asc",
							"start_date_asc",
							"start_date_desc",
							"end_date_asc",
							"end_date_desc"
					})
			)
			@RequestParam(required = false, defaultValue = "default") String sort,
			@Parameter(description = "페이지 번호입니다. 1부터 시작합니다.", example = "1")
			@RequestParam(required = false, defaultValue = "1") Integer page,
			@Parameter(description = "페이지당 항목 수입니다.", example = "10")
			@RequestParam(required = false, defaultValue = "10") Integer size
	) {
		ProjectListResponse response = projectService.listProjects(
				currentUser.id(),
				scope,
				keyword,
				projectName,
				contractNo,
				assigneeUserId,
				status,
				periodFrom,
				periodTo,
				sort,
				page,
				size
		);
		return ResponseEntity.ok(ApiResponse.success(response, "프로젝트 목록 조회에 성공했습니다."));
	}

	@PostMapping
	@Operation(
			summary = "프로젝트 생성",
			description = "프로젝트 기본 정보와 계약/공사 기간/예산 정보를 입력해 새 프로젝트를 생성합니다."
	)
	public ResponseEntity<ApiResponse<ProjectDetailDataResponse>> createProject(
			@Parameter(hidden = true)
			@AuthenticationPrincipal AuthenticatedUser currentUser,
			@Valid @RequestBody ProjectCreateRequest request
	) {
		ProjectDetailDataResponse response = projectService.createProject(currentUser.id(), request);
		return ResponseEntity
				.status(HttpStatus.CREATED)
				.body(ApiResponse.success(response, "프로젝트 생성에 성공했습니다."));
	}

	@GetMapping("/{projectId}")
	@Operation(
			summary = "프로젝트 상세 조회",
			description = "프로젝트 ID로 상세 정보와 현재 담당자 목록을 조회합니다."
	)
	public ResponseEntity<ApiResponse<ProjectDetailDataResponse>> getProject(
			@Parameter(hidden = true)
			@AuthenticationPrincipal AuthenticatedUser currentUser,
			@Parameter(description = "조회할 프로젝트 ID", example = "1")
			@PathVariable Long projectId
	) {
		ProjectDetailDataResponse response = projectService.getProject(currentUser.id(), projectId);
		return ResponseEntity.ok(ApiResponse.success(response, "프로젝트 조회에 성공했습니다."));
	}

	@PatchMapping("/{projectId}")
	@Operation(
			summary = "프로젝트 수정",
			description = "프로젝트 기본 정보 중 변경할 필드만 전달해 수정합니다."
	)
	public ResponseEntity<ApiResponse<ProjectDetailDataResponse>> updateProject(
			@Parameter(hidden = true)
			@AuthenticationPrincipal AuthenticatedUser currentUser,
			@Parameter(description = "수정할 프로젝트 ID", example = "1")
			@PathVariable Long projectId,
			@Valid @RequestBody ProjectUpdateRequest request
	) {
		ProjectDetailDataResponse response = projectService.updateProject(currentUser.id(), projectId, request);
		return ResponseEntity.ok(ApiResponse.success(response, "프로젝트 수정에 성공했습니다."));
	}

	@DeleteMapping("/{projectId}")
	@Operation(
			summary = "프로젝트 삭제",
			description = "프로젝트 ID로 프로젝트를 삭제합니다."
	)
	public ResponseEntity<ApiResponse<Void>> deleteProject(
			@Parameter(hidden = true)
			@AuthenticationPrincipal AuthenticatedUser currentUser,
			@Parameter(description = "삭제할 프로젝트 ID", example = "1")
			@PathVariable Long projectId
	) {
		projectService.deleteProject(currentUser.id(), projectId);
		return ResponseEntity.ok(ApiResponse.success(null, "프로젝트 삭제에 성공했습니다."));
	}

	@GetMapping("/{projectId}/assignees")
	@Operation(
			tags = "프로젝트 담당자",
			summary = "프로젝트 담당자 목록 조회",
			description = "특정 프로젝트에 배정된 담당자 목록을 조회합니다."
	)
	public ResponseEntity<ApiResponse<ProjectAssigneeListResponse>> listAssignees(
			@Parameter(hidden = true)
			@AuthenticationPrincipal AuthenticatedUser currentUser,
			@Parameter(description = "담당자를 조회할 프로젝트 ID", example = "1")
			@PathVariable Long projectId
	) {
		ProjectAssigneeListResponse response = projectService.listAssignees(currentUser.id(), projectId);
		return ResponseEntity.ok(ApiResponse.success(response, "프로젝트 담당자 조회에 성공했습니다."));
	}

	@PutMapping("/{projectId}/assignees")
	@Operation(
			tags = "프로젝트 담당자",
			summary = "프로젝트 담당자 전체 교체",
			description = "전달한 사용자 ID 목록으로 프로젝트 담당자 목록을 전체 교체합니다."
	)
	public ResponseEntity<ApiResponse<ProjectAssigneeListResponse>> replaceAssignees(
			@Parameter(hidden = true)
			@AuthenticationPrincipal AuthenticatedUser currentUser,
			@Parameter(description = "담당자를 교체할 프로젝트 ID", example = "1")
			@PathVariable Long projectId,
			@Valid @RequestBody ReplaceProjectAssigneesRequest request
	) {
		ProjectAssigneeListResponse response = projectService.replaceAssignees(
				currentUser.id(),
				projectId,
				request.assigneeUserIds()
		);
		return ResponseEntity.ok(ApiResponse.success(response, "프로젝트 담당자 교체에 성공했습니다."));
	}

	@PostMapping("/{projectId}/assignees/{userId}")
	@Operation(
			tags = "프로젝트 담당자",
			summary = "프로젝트 담당자 추가",
			description = "특정 사용자를 프로젝트 담당자로 추가합니다."
	)
	public ResponseEntity<ApiResponse<Void>> addAssignee(
			@Parameter(hidden = true)
			@AuthenticationPrincipal AuthenticatedUser currentUser,
			@Parameter(description = "담당자를 추가할 프로젝트 ID", example = "1")
			@PathVariable Long projectId,
			@Parameter(description = "추가할 사용자 ID", example = "3")
			@PathVariable Long userId
	) {
		projectService.addAssignee(currentUser.id(), projectId, userId);
		return ResponseEntity.ok(ApiResponse.success(null, "프로젝트 담당자 추가에 성공했습니다."));
	}

	@DeleteMapping("/{projectId}/assignees/{userId}")
	@Operation(
			tags = "프로젝트 담당자",
			summary = "프로젝트 담당자 제거",
			description = "특정 사용자를 프로젝트 담당자 목록에서 제거합니다."
	)
	public ResponseEntity<ApiResponse<Void>> removeAssignee(
			@Parameter(hidden = true)
			@AuthenticationPrincipal AuthenticatedUser currentUser,
			@Parameter(description = "담당자를 제거할 프로젝트 ID", example = "1")
			@PathVariable Long projectId,
			@Parameter(description = "제거할 사용자 ID", example = "3")
			@PathVariable Long userId
	) {
		projectService.removeAssignee(currentUser.id(), projectId, userId);
		return ResponseEntity.ok(ApiResponse.success(null, "프로젝트 담당자 제거에 성공했습니다."));
	}
}
