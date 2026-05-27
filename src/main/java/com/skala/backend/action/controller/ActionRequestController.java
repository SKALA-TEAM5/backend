package com.skala.backend.action.controller;

import com.skala.backend.action.dto.ActionRequests;
import com.skala.backend.action.dto.ActionResponses;
import com.skala.backend.action.service.ActionRequestService;
import com.skala.backend.auth.security.AuthenticatedUser;
import com.skala.backend.global.config.OpenApiConfig;
import com.skala.backend.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/projects/{projectId}/action-requests")
@Tag(name = "조치 요청", description = "admin이 user에게 조치를 요청하고 상태를 관리하는 API")
@SecurityRequirement(name = OpenApiConfig.COOKIE_AUTH)
public class ActionRequestController {

	private final ActionRequestService actionRequestService;

	public ActionRequestController(ActionRequestService actionRequestService) {
		this.actionRequestService = actionRequestService;
	}

	@PostMapping
	@Operation(summary = "조치 요청 생성 (R-38)", description = "admin만 생성할 수 있습니다.")
	public ResponseEntity<ApiResponse<ActionResponses.ActionRequestResponse>> create(
			@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser currentUser,
			@PathVariable Long projectId,
			@Valid @RequestBody ActionRequests.CreateRequest request
	) {
		ActionResponses.ActionRequestResponse response = actionRequestService.create(currentUser.id(), projectId, request);
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(ApiResponse.success(response, "조치 요청이 생성되었습니다."));
	}

	@PatchMapping("/{actionRequestId}/status")
	@Operation(summary = "조치 요청 상태 업데이트 (R-39)", description = "open→in_progress→closed 순서로 전환합니다.")
	public ResponseEntity<ApiResponse<ActionResponses.ActionRequestResponse>> updateStatus(
			@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser currentUser,
			@PathVariable Long projectId,
			@PathVariable Long actionRequestId,
			@Valid @RequestBody ActionRequests.UpdateStatusRequest request
	) {
		ActionResponses.ActionRequestResponse response = actionRequestService.updateStatus(currentUser.id(), projectId, actionRequestId, request);
		return ResponseEntity.ok(ApiResponse.success(response, "조치 요청 상태가 업데이트되었습니다."));
	}

	@GetMapping
	@Operation(summary = "조치 요청 목록 조회 (R-40)")
	public ResponseEntity<ApiResponse<List<ActionResponses.ActionRequestResponse>>> list(
			@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser currentUser,
			@PathVariable Long projectId
	) {
		List<ActionResponses.ActionRequestResponse> response = actionRequestService.list(currentUser.id(), projectId);
		return ResponseEntity.ok(ApiResponse.success(response, "조치 요청 목록 조회에 성공했습니다."));
	}

	@GetMapping("/{actionRequestId}")
	@Operation(summary = "조치 요청 상세 조회 (R-40)")
	public ResponseEntity<ApiResponse<ActionResponses.ActionRequestResponse>> getDetail(
			@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser currentUser,
			@PathVariable Long projectId,
			@PathVariable Long actionRequestId
	) {
		ActionResponses.ActionRequestResponse response = actionRequestService.getDetail(currentUser.id(), projectId, actionRequestId);
		return ResponseEntity.ok(ApiResponse.success(response, "조치 요청 상세 조회에 성공했습니다."));
	}
}
