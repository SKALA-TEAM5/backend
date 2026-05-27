package com.skala.backend.project.service;

import com.skala.backend.evidence.repository.EvidenceFileLinkRepository;
import com.skala.backend.global.error.ApiException;
import com.skala.backend.project.domain.Project;
import com.skala.backend.project.domain.ProjectSort;
import com.skala.backend.project.domain.ProjectStatusCode;
import com.skala.backend.project.domain.ProjectUserAssignment;
import com.skala.backend.project.dto.ProjectRequests;
import com.skala.backend.project.dto.ProjectResponses;
import com.skala.backend.project.repository.ProjectCardRow;
import com.skala.backend.project.repository.ProjectRepository;
import com.skala.backend.project.repository.ProjectUserAssignmentRepository;
import com.skala.backend.user.domain.RoleCode;
import com.skala.backend.user.domain.User;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

@Service
public class ProjectService {

	private static final int MAX_PAGE_SIZE = 10;

	private final ProjectAccessService projectAccessService;
	private final ProjectRepository projectRepository;
	private final ProjectUserAssignmentRepository assignmentRepository;
	private final EvidenceFileLinkRepository linkRepository;

	public ProjectService(
			ProjectAccessService projectAccessService,
			ProjectRepository projectRepository,
			ProjectUserAssignmentRepository assignmentRepository,
			EvidenceFileLinkRepository linkRepository
	) {
		this.projectAccessService = projectAccessService;
		this.projectRepository = projectRepository;
		this.assignmentRepository = assignmentRepository;
		this.linkRepository = linkRepository;
	}

	@Transactional(readOnly = true)
	public ProjectResponses.ListResponse listProjects(
			Long currentUserId,
			String scope,
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
		projectAccessService.requireProjectWorkAccessible(currentUserId);
		User currentUser = projectAccessService.requireCurrentUser(currentUserId);
		validateDateRange(periodFrom, periodTo);
		int pageNumber = validatePage(page);
		int pageSize = validateSize(size);
		ProjectSort projectSort = ProjectSort.from(sort);

		if (!isProjectManager(currentUser) && assigneeUserId != null) {
			throw new ApiException(HttpStatus.FORBIDDEN, "권한이 없습니다.");
		}

		Long visibleUserId = resolveVisibleUserId(currentUser, scope);
		Page<ProjectCardRow> result = projectRepository.searchCards(
				containsPattern(keyword),
				containsPattern(projectName),
				containsPattern(contractNo),
				assigneeUserId,
				status,
				periodFrom,
				periodTo,
				visibleUserId,
				projectSort,
				pageNumber,
				pageSize
		);

		List<ProjectResponses.CardResponse> items = result.getContent()
				.stream()
				.map(row -> ProjectResponses.CardResponse.from(row, isProjectManager(currentUser)))
				.toList();

		return new ProjectResponses.ListResponse(
				pageNumber,
				pageSize,
				result.getTotalElements(),
				result.getTotalPages(),
				items
		);
	}

	@Transactional
	public ProjectResponses.DetailDataResponse createProject(Long currentUserId, ProjectRequests.CreateRequest request) {
		User creator = projectAccessService.requireAdmin(currentUserId);
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

		Project savedProject = projectRepository.save(project);
		assignmentRepository.save(ProjectUserAssignment.create(savedProject, creator, creator));
		return toDetailDataResponse(savedProject);
	}

	@Transactional(readOnly = true)
	public ProjectResponses.DetailDataResponse getProject(Long currentUserId, Long projectId) {
		Project project = projectAccessService.requireReadable(currentUserId, projectId);
		return toDetailDataResponse(project);
	}

	@Transactional
	public ProjectResponses.DetailDataResponse updateProject(Long currentUserId, Long projectId, ProjectRequests.UpdateRequest request) {
		projectAccessService.requireProjectManager(currentUserId);
		if (request.isEmpty()) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "수정할 값이 없습니다.");
		}

		Project project = findProject(projectId);
		applyUpdate(project, request);
		validateDateRange(project.getConstructionStartDate(), project.getConstructionEndDate());
		return toDetailDataResponse(project);
	}

	@Transactional
	public void deleteProject(Long currentUserId, Long projectId) {
		projectAccessService.requireProjectManager(currentUserId);
		Project project = findProject(projectId);
		try {
			assignmentRepository.deleteByProjectId(projectId);
			projectRepository.delete(project);
			projectRepository.flush();
		} catch (DataIntegrityViolationException exception) {
			throw new ApiException(HttpStatus.CONFLICT, "연결된 데이터가 있어 프로젝트를 삭제할 수 없습니다.");
		}
	}

	private ProjectResponses.DetailDataResponse toDetailDataResponse(Project project) {
		List<ProjectResponses.AssigneeResponse> assignees = assignmentRepository.findByProjectIdOrderByIdAsc(project.getId())
				.stream()
				.map(ProjectResponses.AssigneeResponse::from)
				.toList();

		return new ProjectResponses.DetailDataResponse(ProjectResponses.DetailResponse.of(
				project,
				assignees,
				linkRepository.countUncheckedMatchedFiles(project.getId())
		));
	}

	private void applyUpdate(Project project, ProjectRequests.UpdateRequest request) {
		if (request.contractNo() != null) project.updateContractNo(requireText("contractNo", request.contractNo()));
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

	private boolean isProjectManager(User user) {
		return user.getRoleCode() == RoleCode.ADMIN || user.getRoleCode() == RoleCode.AGENT;
	}

	private Long resolveVisibleUserId(User currentUser, String scope) {
		String normalizedScope = normalizeScope(currentUser, scope);
		return "all".equals(normalizedScope) ? null : currentUser.getId();
	}

	private String normalizeScope(User currentUser, String scope) {
		String normalized = normalize(scope);
		if (normalized != null) {
			normalized = normalized.toLowerCase(Locale.ROOT);
		}
		if (normalized == null) {
			return isProjectManager(currentUser) ? "all" : "assigned";
		}
		if (!normalized.equals("all") && !normalized.equals("assigned")) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "scope는 all 또는 assigned만 사용할 수 있습니다.");
		}
		if (!isProjectManager(currentUser) && normalized.equals("all")) {
			throw new ApiException(HttpStatus.FORBIDDEN, "권한이 없습니다.");
		}
		return normalized;
	}

	private Project findProject(Long projectId) {
		return projectRepository.findById(projectId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "프로젝트를 찾을 수 없습니다."));
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
