package com.skala.backend.action.service;

import com.skala.backend.action.domain.ActionRequest;
import com.skala.backend.action.domain.ActionRequestStatus;
import com.skala.backend.action.dto.ActionRequests;
import com.skala.backend.action.dto.ActionResponses;
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
	public ActionResponses.ActionRequestResponse create(Long currentUserId, Long projectId, ActionRequests.CreateRequest request) {
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
		return ActionResponses.ActionRequestResponse.from(actionRequestRepository.save(actionRequest));
	}

	@Transactional
	public ActionResponses.ActionRequestResponse updateStatus(Long currentUserId, Long projectId, Long actionRequestId, ActionRequests.UpdateStatusRequest request) {
		projectAccessService.requireReadable(currentUserId, projectId);
		ActionRequest actionRequest = actionRequestRepository.findByIdAndProjectId(actionRequestId, projectId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "조치 요청을 찾을 수 없습니다."));
		actionRequest.updateStatus(ActionRequestStatus.from(request.statusCode()));
		return ActionResponses.ActionRequestResponse.from(actionRequest);
	}

	@Transactional(readOnly = true)
	public List<ActionResponses.ActionRequestResponse> list(Long currentUserId, Long projectId) {
		projectAccessService.requireReadable(currentUserId, projectId);
		return actionRequestRepository.findByProjectIdOrderByCreatedAtDesc(projectId)
				.stream()
				.map(ActionResponses.ActionRequestResponse::from)
				.toList();
	}

	@Transactional(readOnly = true)
	public ActionResponses.ActionRequestResponse getDetail(Long currentUserId, Long projectId, Long actionRequestId) {
		projectAccessService.requireReadable(currentUserId, projectId);
		ActionRequest actionRequest = actionRequestRepository.findByIdAndProjectId(actionRequestId, projectId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "조치 요청을 찾을 수 없습니다."));
		return ActionResponses.ActionRequestResponse.from(actionRequest);
	}
}
