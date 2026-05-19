package com.skala.backend.project.service;

import com.skala.backend.global.error.ApiException;
import com.skala.backend.project.domain.Project;
import com.skala.backend.project.domain.ProjectUserAssignment;
import com.skala.backend.project.dto.ProjectAssigneeListResponse;
import com.skala.backend.project.dto.ProjectAssigneeResponse;
import com.skala.backend.project.repository.ProjectRepository;
import com.skala.backend.project.repository.ProjectUserAssignmentRepository;
import com.skala.backend.user.domain.User;
import com.skala.backend.user.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class ProjectAssigneeService {

	private final ProjectAccessService projectAccessService;
	private final ProjectUserAssignmentRepository assignmentRepository;
	private final UserRepository userRepository;
	private final ProjectRepository projectRepository;

	public ProjectAssigneeService(
			ProjectAccessService projectAccessService,
			ProjectUserAssignmentRepository assignmentRepository,
			UserRepository userRepository,
			ProjectRepository projectRepository
	) {
		this.projectAccessService = projectAccessService;
		this.assignmentRepository = assignmentRepository;
		this.userRepository = userRepository;
		this.projectRepository = projectRepository;
	}

	@Transactional(readOnly = true)
	public ProjectAssigneeListResponse listAssignees(Long currentUserId, Long projectId) {
		projectAccessService.requireReadable(currentUserId, projectId);
		return toAssigneeListResponse(projectId);
	}

	@Transactional
	public ProjectAssigneeListResponse replaceAssignees(Long currentUserId, Long projectId, List<Long> assigneeUserIds) {
		User assignedBy = projectAccessService.requireProjectManager(currentUserId);
		Project project = findProject(projectId);
		List<Long> userIds = validateAssigneeIds(assigneeUserIds);

		List<User> users = userRepository.findAllById(userIds);
		if (users.size() != userIds.size()) {
			throw new ApiException(HttpStatus.NOT_FOUND, "존재하지 않는 사용자가 포함되어 있습니다.");
		}

		assignmentRepository.deleteByProjectId(projectId);
		assignmentRepository.flush();

		List<ProjectUserAssignment> assignments = users.stream()
				.map(user -> ProjectUserAssignment.create(project, user, assignedBy))
				.toList();
		assignmentRepository.saveAll(assignments);

		return toAssigneeListResponse(projectId);
	}

	@Transactional
	public void addAssignee(Long currentUserId, Long projectId, Long userId) {
		User assignedBy = projectAccessService.requireProjectManager(currentUserId);
		Project project = findProject(projectId);
		User user = findUser(userId);

		if (assignmentRepository.existsByProjectIdAndUserId(projectId, userId)) {
			throw new ApiException(HttpStatus.CONFLICT, "이미 할당된 담당자입니다.");
		}

		assignmentRepository.save(ProjectUserAssignment.create(project, user, assignedBy));
	}

	@Transactional
	public void removeAssignee(Long currentUserId, Long projectId, Long userId) {
		projectAccessService.requireProjectManager(currentUserId);
		ProjectUserAssignment assignment = assignmentRepository.findByProjectIdAndUserId(projectId, userId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "할당 정보를 찾을 수 없습니다."));
		assignmentRepository.delete(assignment);
	}

	private ProjectAssigneeListResponse toAssigneeListResponse(Long projectId) {
		List<ProjectAssigneeResponse> assignees = assignmentRepository.findByProjectIdOrderByIdAsc(projectId)
				.stream()
				.map(ProjectAssigneeResponse::from)
				.toList();
		return new ProjectAssigneeListResponse(projectId, assignees);
	}

	private Project findProject(Long projectId) {
		return projectRepository.findById(projectId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "프로젝트를 찾을 수 없습니다."));
	}

	private User findUser(Long userId) {
		return userRepository.findById(userId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."));
	}

	private List<Long> validateAssigneeIds(List<Long> userIds) {
		if (userIds == null) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "assigneeUserIds는 필수입니다.");
		}
		Set<Long> unique = new HashSet<>(userIds);
		if (unique.size() != userIds.size()) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "담당자 목록에 중복이 있습니다.");
		}
		return userIds;
	}
}
