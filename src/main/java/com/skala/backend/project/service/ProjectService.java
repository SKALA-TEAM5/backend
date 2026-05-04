package com.skala.backend.project.service;

import com.skala.backend.global.error.ApiException;
import com.skala.backend.project.domain.Project;
import com.skala.backend.project.domain.ProjectStatusCode;
import com.skala.backend.project.domain.ProjectUserAssignment;
import com.skala.backend.project.dto.ProjectAssigneeListResponse;
import com.skala.backend.project.dto.ProjectAssigneeResponse;
import com.skala.backend.project.dto.ProjectCardResponse;
import com.skala.backend.project.dto.ProjectCreateRequest;
import com.skala.backend.project.dto.ProjectDetailDataResponse;
import com.skala.backend.project.dto.ProjectDetailResponse;
import com.skala.backend.project.dto.ProjectListResponse;
import com.skala.backend.project.dto.ProjectUpdateRequest;
import com.skala.backend.project.repository.ProjectRepository;
import com.skala.backend.project.repository.ProjectUserAssignmentRepository;
import com.skala.backend.user.domain.RoleCode;
import com.skala.backend.user.domain.User;
import com.skala.backend.user.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class ProjectService {

	private static final int MAX_PAGE_SIZE = 10;

	private final ProjectRepository projectRepository;
	private final ProjectUserAssignmentRepository assignmentRepository;
	private final UserRepository userRepository;

	public ProjectService(
			ProjectRepository projectRepository,
			ProjectUserAssignmentRepository assignmentRepository,
			UserRepository userRepository
	) {
		this.projectRepository = projectRepository;
		this.assignmentRepository = assignmentRepository;
		this.userRepository = userRepository;
	}

	@Transactional(readOnly = true)
	public ProjectListResponse listProjects(
			String accessToken,
			String keyword,
			String projectName,
			String contractNo,
			Long assigneeUserId,
			ProjectStatusCode status,
			LocalDate periodFrom,
			LocalDate periodTo,
			String sort,
			Integer page,
			Integer size
	) {
		User currentUser = requireCurrentUser(accessToken);
		validateDateRange(periodFrom, periodTo);
		int pageNumber = validatePage(page);
		int pageSize = validateSize(size);

		if (!canManageProjects(currentUser) && assigneeUserId != null) {
			throw new ApiException(HttpStatus.FORBIDDEN, "권한이 없습니다.");
		}

		Long visibleUserId = canManageProjects(currentUser) ? null : currentUser.getId();
		Page<Project> result = projectRepository.search(
				containsPattern(keyword),
				containsPattern(projectName),
				containsPattern(contractNo),
				assigneeUserId,
				status,
				periodFrom,
				periodTo,
				visibleUserId,
				PageRequest.of(pageNumber - 1, pageSize)
		);

		List<ProjectCardResponse> items = result.getContent()
				.stream()
				.map(this::toCardResponse)
				.toList();

		return new ProjectListResponse(
				pageNumber,
				pageSize,
				result.getTotalElements(),
				result.getTotalPages(),
				items
		);
	}

	@Transactional
	public ProjectDetailDataResponse createProject(String accessToken, ProjectCreateRequest request) {
		requireProjectManager(accessToken);
		validateDateRange(request.constructionStartDate(), request.constructionEndDate());

		Project project = Project.create(
				request.contractNo(),
				request.constructionCompany(),
				request.projectName(),
				request.siteLocation(),
				request.representativeName(),
				request.contractAmount(),
				request.constructionStartDate(),
				request.constructionEndDate(),
				request.clientName(),
				request.appropriatedAmount(),
				request.status()
		);

		return toDetailDataResponse(projectRepository.save(project));
	}

	@Transactional(readOnly = true)
	public ProjectDetailDataResponse getProject(String accessToken, Long projectId) {
		User currentUser = requireCurrentUser(accessToken);
		Project project = findProject(projectId);
		requireProjectReadable(currentUser, projectId);
		return toDetailDataResponse(project);
	}

	@Transactional
	public ProjectDetailDataResponse updateProject(String accessToken, Long projectId, ProjectUpdateRequest request) {
		requireProjectManager(accessToken);
		if (request.isEmpty()) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "수정할 값이 없습니다.");
		}

		Project project = findProject(projectId);
		applyUpdate(project, request);
		validateDateRange(project.getConstructionStartDate(), project.getConstructionEndDate());
		return toDetailDataResponse(project);
	}

	@Transactional
	public void deleteProject(String accessToken, Long projectId) {
		requireProjectManager(accessToken);
		Project project = findProject(projectId);
		try {
			assignmentRepository.deleteByProjectId(projectId);
			projectRepository.delete(project);
			projectRepository.flush();
		} catch (DataIntegrityViolationException exception) {
			throw new ApiException(HttpStatus.CONFLICT, "연결된 데이터가 있어 프로젝트를 삭제할 수 없습니다.");
		}
	}

	@Transactional(readOnly = true)
	public ProjectAssigneeListResponse listAssignees(String accessToken, Long projectId) {
		User currentUser = requireCurrentUser(accessToken);
		findProject(projectId);
		requireProjectReadable(currentUser, projectId);
		return assigneeListResponse(projectId);
	}

	@Transactional
	public ProjectAssigneeListResponse replaceAssignees(String accessToken, Long projectId, List<Long> assigneeUserIds) {
		User assignedBy = requireProjectManager(accessToken);
		Project project = findProject(projectId);
		List<Long> userIds = validateAssigneeIds(assigneeUserIds);

		assignmentRepository.deleteByProjectId(projectId);
		assignmentRepository.flush();
		for (Long userId : userIds) {
			User user = findUser(userId);
			assignmentRepository.save(ProjectUserAssignment.create(project, user, assignedBy));
		}

		return assigneeListResponse(projectId);
	}

	@Transactional
	public void addAssignee(String accessToken, Long projectId, Long userId) {
		User assignedBy = requireProjectManager(accessToken);
		Project project = findProject(projectId);
		User user = findUser(userId);

		if (assignmentRepository.existsByProjectIdAndUserId(projectId, userId)) {
			throw new ApiException(HttpStatus.CONFLICT, "이미 할당된 담당자입니다.");
		}

		assignmentRepository.save(ProjectUserAssignment.create(project, user, assignedBy));
	}

	@Transactional
	public void removeAssignee(String accessToken, Long projectId, Long userId) {
		requireProjectManager(accessToken);
		findProject(projectId);
		ProjectUserAssignment assignment = assignmentRepository.findByProjectIdAndUserId(projectId, userId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "할당 정보를 찾을 수 없습니다."));

		assignmentRepository.delete(assignment);
	}

	private ProjectCardResponse toCardResponse(Project project) {
		return ProjectCardResponse.of(
				project,
				assignmentRepository.countByProjectId(project.getId()),
				projectRepository.findLatestProgressRate(project.getId()),
				projectRepository.hasOpenActionRequest(project.getId())
		);
	}

	private ProjectDetailDataResponse toDetailDataResponse(Project project) {
		List<ProjectAssigneeResponse> assignees = assignmentRepository.findByProjectIdOrderByIdAsc(project.getId())
				.stream()
				.map(ProjectAssigneeResponse::from)
				.toList();

		return new ProjectDetailDataResponse(ProjectDetailResponse.of(project, assignees));
	}

	private ProjectAssigneeListResponse assigneeListResponse(Long projectId) {
		List<ProjectAssigneeResponse> assignees = assignmentRepository.findByProjectIdOrderByIdAsc(projectId)
				.stream()
				.map(ProjectAssigneeResponse::from)
				.toList();

		return new ProjectAssigneeListResponse(projectId, assignees);
	}

	private void applyUpdate(Project project, ProjectUpdateRequest request) {
		if (request.contractNo() != null) project.updateContractNo(request.contractNo());
		if (request.constructionCompany() != null) project.updateConstructionCompany(requireText("constructionCompany", request.constructionCompany()));
		if (request.projectName() != null) project.updateProjectName(requireText("projectName", request.projectName()));
		if (request.siteLocation() != null) project.updateSiteLocation(requireText("siteLocation", request.siteLocation()));
		if (request.representativeName() != null) project.updateRepresentativeName(request.representativeName());
		if (request.contractAmount() != null) project.updateContractAmount(request.contractAmount());
		if (request.constructionStartDate() != null) project.updateConstructionStartDate(request.constructionStartDate());
		if (request.constructionEndDate() != null) project.updateConstructionEndDate(request.constructionEndDate());
		if (request.clientName() != null) project.updateClientName(request.clientName());
		if (request.appropriatedAmount() != null) project.updateAppropriatedAmount(request.appropriatedAmount());
		if (request.status() != null) project.updateStatus(request.status());
	}

	private User requireProjectManager(String accessToken) {
		User user = requireCurrentUser(accessToken);
		if (!canManageProjects(user)) {
			throw new ApiException(HttpStatus.FORBIDDEN, "권한이 없습니다.");
		}
		return user;
	}

	private void requireProjectReadable(User currentUser, Long projectId) {
		if (canManageProjects(currentUser)) {
			return;
		}
		if (!assignmentRepository.existsByProjectIdAndUserId(projectId, currentUser.getId())) {
			throw new ApiException(HttpStatus.FORBIDDEN, "권한이 없습니다.");
		}
	}

	private boolean canManageProjects(User user) {
		return user.getRoleCode() == RoleCode.HQ || user.getRoleCode() == RoleCode.AGENT;
	}

	private User requireCurrentUser(String accessToken) {
		if (!StringUtils.hasText(accessToken)) {
			throw new ApiException(HttpStatus.UNAUTHORIZED, "인증이 필요합니다.");
		}

		try {
			Long userId = Long.parseLong(accessToken);
			return userRepository.findById(userId)
					.orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "유효하지 않은 인증 정보입니다."));
		} catch (NumberFormatException exception) {
			throw new ApiException(HttpStatus.UNAUTHORIZED, "유효하지 않은 인증 정보입니다.");
		}
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

		Set<Long> uniqueUserIds = new HashSet<>(userIds);
		if (uniqueUserIds.size() != userIds.size()) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "담당자 목록에 중복이 있습니다.");
		}

		return userIds;
	}

	private void validateDateRange(LocalDate from, LocalDate to) {
		if (from != null && to != null && to.isBefore(from)) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "종료일은 시작일보다 빠를 수 없습니다.");
		}
	}

	private int validatePage(Integer page) {
		int value = page == null ? 1 : page;
		if (value < 1) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "page는 1 이상이어야 합니다.");
		}
		return value;
	}

	private int validateSize(Integer size) {
		int value = size == null ? MAX_PAGE_SIZE : size;
		if (value < 1 || value > MAX_PAGE_SIZE) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "size는 1 이상 10 이하여야 합니다.");
		}
		return value;
	}

	private String normalize(String value) {
		return StringUtils.hasText(value) ? value.trim() : null;
	}

	private String containsPattern(String value) {
		String normalized = normalize(value);
		return normalized == null ? null : "%" + normalized.toLowerCase() + "%";
	}

	private String requireText(String fieldName, String value) {
		if (!StringUtils.hasText(value)) {
			throw new ApiException(HttpStatus.BAD_REQUEST, fieldName + ": 공백일 수 없습니다.");
		}
		return value;
	}
}
