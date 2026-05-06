package com.skala.backend.project.service;

import com.skala.backend.global.error.ApiException;
import com.skala.backend.project.domain.Project;
import com.skala.backend.project.repository.ProjectRepository;
import com.skala.backend.project.repository.ProjectUserAssignmentRepository;
import com.skala.backend.user.domain.RoleCode;
import com.skala.backend.user.domain.User;
import com.skala.backend.user.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class ProjectAccessService {

	private final ProjectRepository projectRepository;
	private final ProjectUserAssignmentRepository assignmentRepository;
	private final UserRepository userRepository;

	public ProjectAccessService(
			ProjectRepository projectRepository,
			ProjectUserAssignmentRepository assignmentRepository,
			UserRepository userRepository
	) {
		this.projectRepository = projectRepository;
		this.assignmentRepository = assignmentRepository;
		this.userRepository = userRepository;
	}

	public Project requireReadable(Long currentUserId, Long projectId) {
		User currentUser = requireCurrentUser(currentUserId);
		Project project = requireProject(projectId);
		requireProjectAccess(currentUser, projectId);
		return project;
	}

	public Project requireWritable(Long currentUserId, Long projectId) {
		User currentUser = requireCurrentUser(currentUserId);
		Project project = requireProject(projectId);
		requireProjectAccess(currentUser, projectId);
		return project;
	}

	public User requireCurrentUser(Long currentUserId) {
		if (currentUserId == null) {
			throw new ApiException(HttpStatus.UNAUTHORIZED, "인증이 필요합니다.");
		}
		return userRepository.findById(currentUserId)
				.orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "유효하지 않은 인증 정보입니다."));
	}

	private Project requireProject(Long projectId) {
		return projectRepository.findById(projectId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "프로젝트를 찾을 수 없습니다."));
	}

	private void requireProjectAccess(User currentUser, Long projectId) {
		if (currentUser.getRoleCode() == RoleCode.ADMIN || currentUser.getRoleCode() == RoleCode.AGENT) {
			return;
		}
		if (currentUser.getRoleCode() == RoleCode.USER
				&& assignmentRepository.existsByProjectIdAndUserId(projectId, currentUser.getId())) {
			return;
		}
		throw new ApiException(HttpStatus.FORBIDDEN, "권한이 없습니다.");
	}
}
