package com.skala.backend.action.service;

import com.skala.backend.action.domain.ActionRequest;
import com.skala.backend.action.domain.ActionRequestStatus;
import com.skala.backend.action.dto.ActionRequestDtos.ActionRequestResponse;
import com.skala.backend.action.dto.ActionRequestDtos.CreateActionRequestRequest;
import com.skala.backend.action.dto.ActionRequestDtos.UpdateActionRequestStatusRequest;
import com.skala.backend.action.repository.ActionRequestRepository;
import com.skala.backend.global.error.ApiException;
import com.skala.backend.project.service.ProjectAccessService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ActionRequestService {

	private final ActionRequestRepository actionRequestRepository;
	private final ProjectAccessService projectAccessService;

	public ActionRequestService(
			ActionRequestRepository actionRequestRepository,
			ProjectAccessService projectAccessService
	) {
		this.actionRequestRepository = actionRequestRepository;
		this.projectAccessService = projectAccessService;
	}

	@Transactional
	public ActionRequestResponse create(Long currentUserId, Long projectId, CreateActionRequestRequest request) {
		projectAccessService.requireAdmin(currentUserId);
		ActionRequest actionRequest = ActionRequest.create(
				projectId,
				currentUserId,
				request.assigneeUserId(),
				request.usageStatementId(),
				request.usageStatementItemId(),
				request.title(),
				request.reason(),
				request.dueDate()
		);
		return ActionRequestResponse.from(actionRequestRepository.save(actionRequest));
	}

	@Transactional
	public ActionRequestResponse updateStatus(Long currentUserId, Long projectId, Long actionRequestId, UpdateActionRequestStatusRequest request) {
		projectAccessService.requireReadable(currentUserId, projectId);
		ActionRequest actionRequest = actionRequestRepository.findByIdAndProjectId(actionRequestId, projectId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "조치 요청을 찾을 수 없습니다."));
		actionRequest.updateStatus(ActionRequestStatus.from(request.statusCode()));
		return ActionRequestResponse.from(actionRequest);
	}

	@Transactional(readOnly = true)
	public List<ActionRequestResponse> list(Long currentUserId, Long projectId) {
		projectAccessService.requireReadable(currentUserId, projectId);
		return actionRequestRepository.findByProjectIdOrderByCreatedAtDesc(projectId)
				.stream()
				.map(ActionRequestResponse::from)
				.toList();
	}

	@Transactional(readOnly = true)
	public ActionRequestResponse getDetail(Long currentUserId, Long projectId, Long actionRequestId) {
		projectAccessService.requireReadable(currentUserId, projectId);
		ActionRequest actionRequest = actionRequestRepository.findByIdAndProjectId(actionRequestId, projectId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "조치 요청을 찾을 수 없습니다."));
		return ActionRequestResponse.from(actionRequest);
	}
}
