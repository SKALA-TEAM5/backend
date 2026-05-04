package com.skala.backend.project.controller;

import com.skala.backend.global.response.ApiResponse;
import com.skala.backend.project.domain.ProjectStatusCode;
import com.skala.backend.project.dto.ProjectAssigneeListResponse;
import com.skala.backend.project.dto.ProjectCreateRequest;
import com.skala.backend.project.dto.ProjectDetailDataResponse;
import com.skala.backend.project.dto.ProjectListResponse;
import com.skala.backend.project.dto.ProjectUpdateRequest;
import com.skala.backend.project.dto.ReplaceProjectAssigneesRequest;
import com.skala.backend.project.service.ProjectService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
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
public class ProjectController {

	private final ProjectService projectService;

	public ProjectController(ProjectService projectService) {
		this.projectService = projectService;
	}

	@GetMapping
	public ResponseEntity<ApiResponse<ProjectListResponse>> listProjects(
			@CookieValue(name = "access_token", required = false) String accessToken,
			@RequestParam(required = false) String keyword,
			@RequestParam(required = false) String projectName,
			@RequestParam(required = false) String contractNo,
			@RequestParam(required = false) Long assigneeUserId,
			@RequestParam(required = false) ProjectStatusCode status,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodFrom,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodTo,
			@RequestParam(required = false, defaultValue = "default") String sort,
			@RequestParam(required = false, defaultValue = "1") Integer page,
			@RequestParam(required = false, defaultValue = "10") Integer size
	) {
		ProjectListResponse response = projectService.listProjects(
				accessToken,
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
	public ResponseEntity<ApiResponse<ProjectDetailDataResponse>> createProject(
			@CookieValue(name = "access_token", required = false) String accessToken,
			@Valid @RequestBody ProjectCreateRequest request
	) {
		ProjectDetailDataResponse response = projectService.createProject(accessToken, request);
		return ResponseEntity
				.status(HttpStatus.CREATED)
				.body(ApiResponse.success(response, "프로젝트 생성에 성공했습니다."));
	}

	@GetMapping("/{projectId}")
	public ResponseEntity<ApiResponse<ProjectDetailDataResponse>> getProject(
			@CookieValue(name = "access_token", required = false) String accessToken,
			@PathVariable Long projectId
	) {
		ProjectDetailDataResponse response = projectService.getProject(accessToken, projectId);
		return ResponseEntity.ok(ApiResponse.success(response, "프로젝트 조회에 성공했습니다."));
	}

	@PatchMapping("/{projectId}")
	public ResponseEntity<ApiResponse<ProjectDetailDataResponse>> updateProject(
			@CookieValue(name = "access_token", required = false) String accessToken,
			@PathVariable Long projectId,
			@Valid @RequestBody ProjectUpdateRequest request
	) {
		ProjectDetailDataResponse response = projectService.updateProject(accessToken, projectId, request);
		return ResponseEntity.ok(ApiResponse.success(response, "프로젝트 수정에 성공했습니다."));
	}

	@DeleteMapping("/{projectId}")
	public ResponseEntity<ApiResponse<Void>> deleteProject(
			@CookieValue(name = "access_token", required = false) String accessToken,
			@PathVariable Long projectId
	) {
		projectService.deleteProject(accessToken, projectId);
		return ResponseEntity.ok(ApiResponse.success(null, "프로젝트 삭제에 성공했습니다."));
	}

	@GetMapping("/{projectId}/assignees")
	public ResponseEntity<ApiResponse<ProjectAssigneeListResponse>> listAssignees(
			@CookieValue(name = "access_token", required = false) String accessToken,
			@PathVariable Long projectId
	) {
		ProjectAssigneeListResponse response = projectService.listAssignees(accessToken, projectId);
		return ResponseEntity.ok(ApiResponse.success(response, "프로젝트 담당자 조회에 성공했습니다."));
	}

	@PutMapping("/{projectId}/assignees")
	public ResponseEntity<ApiResponse<ProjectAssigneeListResponse>> replaceAssignees(
			@CookieValue(name = "access_token", required = false) String accessToken,
			@PathVariable Long projectId,
			@Valid @RequestBody ReplaceProjectAssigneesRequest request
	) {
		ProjectAssigneeListResponse response = projectService.replaceAssignees(
				accessToken,
				projectId,
				request.assigneeUserIds()
		);
		return ResponseEntity.ok(ApiResponse.success(response, "프로젝트 담당자 교체에 성공했습니다."));
	}

	@PostMapping("/{projectId}/assignees/{userId}")
	public ResponseEntity<ApiResponse<Void>> addAssignee(
			@CookieValue(name = "access_token", required = false) String accessToken,
			@PathVariable Long projectId,
			@PathVariable Long userId
	) {
		projectService.addAssignee(accessToken, projectId, userId);
		return ResponseEntity.ok(ApiResponse.success(null, "프로젝트 담당자 추가에 성공했습니다."));
	}

	@DeleteMapping("/{projectId}/assignees/{userId}")
	public ResponseEntity<ApiResponse<Void>> removeAssignee(
			@CookieValue(name = "access_token", required = false) String accessToken,
			@PathVariable Long projectId,
			@PathVariable Long userId
	) {
		projectService.removeAssignee(accessToken, projectId, userId);
		return ResponseEntity.ok(ApiResponse.success(null, "프로젝트 담당자 제거에 성공했습니다."));
	}
}
