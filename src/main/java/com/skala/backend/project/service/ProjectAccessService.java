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

	public User requireAdmin(Long currentUserId) {
		User currentUser = requireCurrentUser(currentUserId);
		if (currentUser.getRoleCode() != RoleCode.ADMIN) {
			throw new ApiException(HttpStatus.FORBIDDEN, "권한이 없습니다.");
		}
		return currentUser;
	}

	public User requireSystemAdmin(Long currentUserId) {
		User currentUser = requireCurrentUser(currentUserId);
		if (currentUser.getRoleCode() != RoleCode.SYSTEM_ADMIN) {
			throw new ApiException(HttpStatus.FORBIDDEN, "권한이 없습니다.");
		}
		return currentUser;
	}

	public User requireSystemAdminOrAdmin(Long currentUserId) {
		User currentUser = requireCurrentUser(currentUserId);
		if (currentUser.getRoleCode() != RoleCode.SYSTEM_ADMIN && currentUser.getRoleCode() != RoleCode.ADMIN) {
			throw new ApiException(HttpStatus.FORBIDDEN, "권한이 없습니다.");
		}
		return currentUser;
	}

	public User requireProjectManager(Long currentUserId) {
		User currentUser = requireCurrentUser(currentUserId);
		if (currentUser.getRoleCode() != RoleCode.ADMIN && currentUser.getRoleCode() != RoleCode.AGENT) {
			throw new ApiException(HttpStatus.FORBIDDEN, "권한이 없습니다.");
		}
		return currentUser;
	}

	/** 해당 프로젝트에 작업인원으로 배정된 admin만 허용. agent·미배정 admin·그 외 역할은 403. */
	public User requireAssignedAdmin(Long currentUserId, Long projectId) {
		User currentUser = requireCurrentUser(currentUserId);
		if (currentUser.getRoleCode() != RoleCode.ADMIN
				|| !assignmentRepository.existsByProjectIdAndUserId(projectId, currentUser.getId())) {
			throw new ApiException(HttpStatus.FORBIDDEN, "권한이 없습니다.");
		}
		return currentUser;
	}

	/** 소속된 admin/user 또는 agent만 허용. 미소속 admin·system_admin은 403. */
	public void requireAssignedMember(Long currentUserId, Long projectId) {
		User currentUser = requireCurrentUser(currentUserId);
		RoleCode role = currentUser.getRoleCode();
		if (role == RoleCode.AGENT) {
			return;
		}
		if ((role == RoleCode.ADMIN || role == RoleCode.USER)
				&& assignmentRepository.existsByProjectIdAndUserId(projectId, currentUser.getId())) {
			return;
		}
		throw new ApiException(HttpStatus.FORBIDDEN, "권한이 없습니다.");
	}

	public User requireProjectManagerOf(Long currentUserId, Long projectId) {
		User currentUser = requireProjectManager(currentUserId);
		if (currentUser.getRoleCode() == RoleCode.ADMIN
				&& !assignmentRepository.existsByProjectIdAndUserId(projectId, currentUser.getId())) {
			throw new ApiException(HttpStatus.FORBIDDEN, "권한이 없습니다.");
		}
		return currentUser;
	}

	public void requireProjectWorkAccessible(Long currentUserId) {
		User currentUser = requireCurrentUser(currentUserId);
		if (currentUser.getRoleCode() == RoleCode.SYSTEM_ADMIN) {
			throw new ApiException(HttpStatus.FORBIDDEN, "권한이 없습니다.");
		}
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
