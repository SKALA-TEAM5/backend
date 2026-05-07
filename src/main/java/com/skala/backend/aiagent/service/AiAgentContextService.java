package com.skala.backend.aiagent.service;

import com.skala.backend.aiagent.client.AiAgentClientDtos.AgentEvidenceFileLinkContext;
import com.skala.backend.aiagent.client.AiAgentClientDtos.AgentFileContext;
import com.skala.backend.aiagent.client.AiAgentClientDtos.AgentUsageStatementContext;
import com.skala.backend.aiagent.client.AiAgentClientDtos.AgentUsageStatementItemContext;
import com.skala.backend.aiagent.domain.AiAgentTypeCode;
import com.skala.backend.aiagent.dto.AiAgentRunRequests.StartAiAgentRunRequest;
import com.skala.backend.evidence.domain.EvidenceFileLink;
import com.skala.backend.evidence.repository.EvidenceFileLinkRepository;
import com.skala.backend.file.domain.ProjectFile;
import com.skala.backend.file.repository.ProjectFileRepository;
import com.skala.backend.global.error.ApiException;
import com.skala.backend.project.domain.Project;
import com.skala.backend.usage.domain.UsageStatement;
import com.skala.backend.usage.domain.UsageStatementItem;
import com.skala.backend.usage.repository.UsageStatementItemRepository;
import com.skala.backend.usage.repository.UsageStatementRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AiAgentContextService {

	private final ProjectFileRepository fileRepository;
	private final UsageStatementRepository usageStatementRepository;
	private final UsageStatementItemRepository usageStatementItemRepository;
	private final EvidenceFileLinkRepository evidenceFileLinkRepository;

	public AiAgentContextService(
			ProjectFileRepository fileRepository,
			UsageStatementRepository usageStatementRepository,
			UsageStatementItemRepository usageStatementItemRepository,
			EvidenceFileLinkRepository evidenceFileLinkRepository
	) {
		this.fileRepository = fileRepository;
		this.usageStatementRepository = usageStatementRepository;
		this.usageStatementItemRepository = usageStatementItemRepository;
		this.evidenceFileLinkRepository = evidenceFileLinkRepository;
	}

	public Map<String, Object> build(Project project, StartAiAgentRunRequest request) {
		UsageStatement usageStatement = resolveUsageStatement(project.getId(), request);
		List<UsageStatementItem> items = resolveItems(usageStatement);
		List<EvidenceFileLink> evidenceLinks = resolveEvidenceLinks(items);
		List<ProjectFile> files = resolveFiles(project.getId(), request, evidenceLinks);
		Map<String, Object> context = new LinkedHashMap<>();
		context.put("project", projectContext(project));
		context.put("usageStatement", usageStatement == null ? null : usageStatementContext(usageStatement));
		context.put("items", items.stream().map(this::itemContext).toList());
		context.put("evidenceLinks", evidenceLinks.stream().map(this::evidenceLinkContext).toList());
		context.put("files", files.stream().map(this::fileContext).toList());
		context.put("options", request.options() == null ? Map.of() : request.options());
		return context;
	}

	private UsageStatement resolveUsageStatement(Long projectId, StartAiAgentRunRequest request) {
		if (request.usageStatementId() != null) {
			return usageStatementRepository.findByIdAndProjectId(request.usageStatementId(), projectId)
					.orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "사용내역서가 프로젝트에 속하지 않습니다."));
		}
		if (request.agentTypeCode() == AiAgentTypeCode.OCR_AGENT || request.agentTypeCode() == AiAgentTypeCode.CLASSIFIER_AGENT) {
			return null;
		}
		return usageStatementRepository.findFirstByProjectIdOrderByReportMonthDescRevisionNoDesc(projectId)
				.orElse(null);
	}

	private List<UsageStatementItem> resolveItems(UsageStatement usageStatement) {
		if (usageStatement == null) {
			return List.of();
		}
		return usageStatementItemRepository.findByUsageStatementIdOrderByPageNoAscUsedOnAscIdAsc(usageStatement.getId());
	}

	private List<EvidenceFileLink> resolveEvidenceLinks(List<UsageStatementItem> items) {
		if (items.isEmpty()) {
			return List.of();
		}
		List<Long> itemIds = items.stream().map(UsageStatementItem::getId).toList();
		return evidenceFileLinkRepository.findByUsageStatementItemIdIn(itemIds);
	}

	private List<ProjectFile> resolveFiles(Long projectId, StartAiAgentRunRequest request, List<EvidenceFileLink> evidenceLinks) {
		if (request.fileIds() == null || request.fileIds().isEmpty()) {
			if (request.agentTypeCode() == AiAgentTypeCode.OCR_AGENT
					|| request.agentTypeCode() == AiAgentTypeCode.CLASSIFIER_AGENT
					|| request.agentTypeCode() == AiAgentTypeCode.VISION_AGENT) {
				return fileRepository.findByProjectIdAndDeletedAtIsNull(projectId);
			}
			return resolveLinkedFiles(projectId, evidenceLinks);
		}

		List<Long> distinctFileIds = request.fileIds().stream().distinct().toList();
		List<ProjectFile> files = fileRepository.findByProjectIdAndIdInAndDeletedAtIsNull(projectId, distinctFileIds);
		if (files.size() != distinctFileIds.size()) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "프로젝트에 속하지 않거나 삭제된 파일이 포함되어 있습니다.");
		}
		return files;
	}

	private List<ProjectFile> resolveLinkedFiles(Long projectId, List<EvidenceFileLink> evidenceLinks) {
		List<Long> fileIds = evidenceLinks.stream()
				.map(EvidenceFileLink::getFileId)
				.filter(Objects::nonNull)
				.distinct()
				.toList();
		if (fileIds.isEmpty()) {
			return List.of();
		}
		Map<Long, ProjectFile> filesById = fileRepository.findByProjectIdAndIdInAndDeletedAtIsNull(projectId, fileIds)
				.stream()
				.collect(Collectors.toMap(ProjectFile::getId, Function.identity()));
		return fileIds.stream()
				.map(filesById::get)
				.filter(Objects::nonNull)
				.toList();
	}

	private Map<String, Object> projectContext(Project project) {
		Map<String, Object> values = new LinkedHashMap<>();
		values.put("projectId", project.getId());
		values.put("contractNo", project.getContractNo());
		values.put("projectName", project.getProjectName());
		values.put("constructionCompany", project.getConstructionCompany());
		values.put("siteLocation", project.getSiteLocation());
		values.put("statusCode", project.getStatus().getValue());
		return values;
	}

	private AgentUsageStatementContext usageStatementContext(UsageStatement statement) {
		return new AgentUsageStatementContext(
				statement.getId(),
				statement.getSourceFileId(),
				statement.getReportMonth(),
				statement.getRevisionNo(),
				statement.getDocumentWrittenDate(),
				statement.getCumulativeProgressRate()
		);
	}

	private AgentUsageStatementItemContext itemContext(UsageStatementItem item) {
		return new AgentUsageStatementItemContext(
				item.getId(),
				item.getUsageStatementId(),
				item.getCategoryCode(),
				item.getUsedOn(),
				item.getItemName(),
				item.getUnit(),
				item.getQuantity(),
				item.getUnitPrice(),
				item.getTotalAmount(),
				item.getRemark(),
				item.getPageNo()
		);
	}

	private AgentEvidenceFileLinkContext evidenceLinkContext(EvidenceFileLink link) {
		return new AgentEvidenceFileLinkContext(
				link.getId(),
				link.getUsageStatementItemId(),
				link.getFileId(),
				link.getCategoryCode(),
				link.getEvidenceTypeCode(),
				link.getCheckedAt()
		);
	}

	private AgentFileContext fileContext(ProjectFile file) {
		return new AgentFileContext(
				file.getId(),
				file.getUploadedEvidenceTypeCode(),
				file.getOriginalFilename(),
				file.getStorageKey(),
				file.getMimeType(),
				file.getSizeBytes(),
				file.getCapturedAt(),
				file.getUploadedAt()
		);
	}
}
