package com.skala.backend.project.service;

import com.skala.backend.global.error.ApiException;
import com.skala.backend.project.domain.Project;
import com.skala.backend.project.domain.ProjectUserAssignment;
import com.skala.backend.project.dto.ProjectResponses;
import com.skala.backend.project.repository.AssigneeCandidateRow;
import com.skala.backend.project.repository.ProjectRepository;
import com.skala.backend.project.repository.ProjectUserAssignmentRepository;
import com.skala.backend.user.domain.RoleCode;
import com.skala.backend.user.domain.User;
import com.skala.backend.user.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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
	public ProjectResponses.AssigneeListResponse listAssignees(Long currentUserId, Long projectId) {
		projectAccessService.requireReadable(currentUserId, projectId);
		return toAssigneeListResponse(projectId);
	}

	@Transactional(readOnly = true)
	public ProjectResponses.AssigneeCandidatesResponse listAssigneeCandidates(Long currentUserId, String keyword) {
		projectAccessService.requireProjectWorkAccessible(currentUserId);
		User currentUser = projectAccessService.requireCurrentUser(currentUserId);
		String keywordPattern = containsPattern(keyword);

		// agent는 전체 프로젝트가 보이므로 풀 제한 없이 전체 담당자 후보를 조회한다.
		List<AssigneeCandidateRow> rows = currentUser.getRoleCode() == RoleCode.AGENT
				? assignmentRepository.findAllAssigneePool(keywordPattern)
				: assignmentRepository.findAssigneePoolByUser(currentUserId, keywordPattern);

		List<ProjectResponses.AssigneeCandidate> candidates = rows.stream()
				.map(row -> new ProjectResponses.AssigneeCandidate(
						row.getUserId(),
						row.getRealName(),
						RoleCode.from(row.getRoleCode())
				))
				.toList();
		return new ProjectResponses.AssigneeCandidatesResponse(candidates);
	}

	@Transactional
	public ProjectResponses.AssigneeListResponse replaceAssignees(Long currentUserId, Long projectId, List<Long> assigneeUserIds) {
		User assignedBy = projectAccessService.requireProjectManagerOf(currentUserId, projectId);
		Project project = findProject(projectId);
		List<Long> userIds = validateAssigneeIds(assigneeUserIds);

		Map<Long, User> userMap = userRepository.findAllById(userIds).stream()
				.collect(Collectors.toMap(User::getId, u -> u));
		if (userMap.size() != userIds.size()) {
			throw new ApiException(HttpStatus.NOT_FOUND, "존재하지 않는 사용자가 포함되어 있습니다.");
		}
		if (userMap.values().stream().anyMatch(u -> u.getRoleCode() == RoleCode.SYSTEM_ADMIN)) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "system_admin은 프로젝트 담당자로 추가할 수 없습니다.");
		}

		// agent가 호출할 때 목록에 admin이 없으면 고아 프로젝트가 될 수 있으므로 차단
		boolean callerIsAdmin = assignedBy.getRoleCode() == RoleCode.ADMIN;
		boolean listHasAdmin = userMap.values().stream().anyMatch(u -> u.getRoleCode() == RoleCode.ADMIN);
		if (!callerIsAdmin && !listHasAdmin) {
			throw new ApiException(HttpStatus.CONFLICT, "프로젝트에 최소 한 명의 admin이 있어야 합니다.");
		}

		assignmentRepository.deleteByProjectId(projectId);
		assignmentRepository.flush();

		List<ProjectUserAssignment> assignments = userIds.stream()
				.map(id -> ProjectUserAssignment.create(project, userMap.get(id), assignedBy))
				.toList();
		assignmentRepository.saveAll(assignments);

		// admin 소유자는 목록에 없어도 항상 소속 유지 (마지막 순서로 추가)
		if (assignedBy.getRoleCode() == RoleCode.ADMIN && !userIds.contains(currentUserId)) {
			assignmentRepository.save(ProjectUserAssignment.create(project, assignedBy, assignedBy));
		}

		return toAssigneeListResponse(projectId);
	}

	@Transactional
	public void addAssignee(Long currentUserId, Long projectId, Long userId) {
		User assignedBy = projectAccessService.requireProjectManagerOf(currentUserId, projectId);
		Project project = findProject(projectId);
		User user = findUser(userId);

		if (user.getRoleCode() == RoleCode.SYSTEM_ADMIN) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "system_admin은 프로젝트 담당자로 추가할 수 없습니다.");
		}
		if (assignmentRepository.existsByProjectIdAndUserId(projectId, userId)) {
			throw new ApiException(HttpStatus.CONFLICT, "이미 할당된 담당자입니다.");
		}

		assignmentRepository.save(ProjectUserAssignment.create(project, user, assignedBy));
	}

	@Transactional
	public void removeAssignee(Long currentUserId, Long projectId, Long userId) {
		projectAccessService.requireProjectManagerOf(currentUserId, projectId);
		User userToRemove = findUser(userId);
		if (userToRemove.getRoleCode() == RoleCode.ADMIN
				&& assignmentRepository.countAdminsByProjectId(projectId) <= 1) {
			throw new ApiException(HttpStatus.CONFLICT, "프로젝트에 최소 한 명의 admin이 있어야 합니다.");
		}
		ProjectUserAssignment assignment = assignmentRepository.findByProjectIdAndUserId(projectId, userId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "할당 정보를 찾을 수 없습니다."));
		assignmentRepository.delete(assignment);
	}

	private ProjectResponses.AssigneeListResponse toAssigneeListResponse(Long projectId) {
		List<ProjectResponses.AssigneeResponse> assignees = assignmentRepository.findByProjectIdOrderByIdAsc(projectId)
				.stream()
				.map(ProjectResponses.AssigneeResponse::from)
				.toList();
		return new ProjectResponses.AssigneeListResponse(projectId, assignees);
	}

	private Project findProject(Long projectId) {
		return projectRepository.findById(projectId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "프로젝트를 찾을 수 없습니다."));
	}

	private User findUser(Long userId) {
		return userRepository.findById(userId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."));
	}

	private String containsPattern(String value) {
		return StringUtils.hasText(value) ? "%" + value.trim().toLowerCase() + "%" : null;
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
