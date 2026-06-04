package com.skala.backend.evidence.service;

import com.skala.backend.evidence.domain.EvidenceFileLink;
import com.skala.backend.evidence.domain.EvidenceRequirement;
import com.skala.backend.evidence.dto.EvidenceRequests.LinkEvidenceFileRequest;
import com.skala.backend.evidence.dto.EvidenceRequests.MoveEvidenceFileLinkRequest;
import com.skala.backend.evidence.dto.EvidenceResponses.EvidenceLinkResponse;
import com.skala.backend.evidence.repository.EvidenceFileLinkRepository;
import com.skala.backend.evidence.repository.EvidenceRequirementRepository;
import com.skala.backend.file.domain.ProjectFile;
import com.skala.backend.file.repository.ProjectFileRepository;
import com.skala.backend.global.error.ApiException;
import com.skala.backend.project.service.CodeLookupService;
import com.skala.backend.project.service.ProjectAccessService;
import com.skala.backend.usage.domain.UsageStatement;
import com.skala.backend.usage.domain.UsageStatementItem;
import com.skala.backend.usage.repository.UsageStatementRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class EvidenceCommandService {

	private final ProjectAccessService projectAccessService;
	private final EvidenceQueryService evidenceQueryService;
	private final EvidenceFileLinkRepository linkRepository;
	private final EvidenceRequirementRepository requirementRepository;
	private final ProjectFileRepository fileRepository;
	private final CodeLookupService codeLookupService;
	private final UsageStatementRepository usageStatementRepository;

	public EvidenceCommandService(
			ProjectAccessService projectAccessService,
			EvidenceQueryService evidenceQueryService,
			EvidenceFileLinkRepository linkRepository,
			EvidenceRequirementRepository requirementRepository,
			ProjectFileRepository fileRepository,
			CodeLookupService codeLookupService,
			UsageStatementRepository usageStatementRepository
	) {
		this.projectAccessService = projectAccessService;
		this.evidenceQueryService = evidenceQueryService;
		this.linkRepository = linkRepository;
		this.requirementRepository = requirementRepository;
		this.fileRepository = fileRepository;
		this.codeLookupService = codeLookupService;
		this.usageStatementRepository = usageStatementRepository;
	}

	@Transactional
	public EvidenceLinkResponse linkFile(Long currentUserId, Long projectId, Long itemId, LinkEvidenceFileRequest request) {
		projectAccessService.requireWritable(currentUserId, projectId);
		requireEvidenceType(request.evidenceTypeCode());
		UsageStatementItem item = evidenceQueryService.requireProjectItem(projectId, itemId);
		ProjectFile file = fileRepository.findByIdAndProjectIdAndDeletedAtIsNull(request.fileId(), projectId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "파일을 찾을 수 없습니다."));

		if (linkRepository.existsByUsageStatementItemIdAndFileId(itemId, file.getId())) {
			throw new ApiException(HttpStatus.CONFLICT, "이미 연결된 파일입니다.");
		}

		try {
			EvidenceFileLink link = linkRepository.save(EvidenceFileLink.create(
					item.getId(),
					file.getId(),
					request.evidenceTypeCode()
			));
			recalculateRequirements(item.getId());
			revertToDraftIfSupplementRequired(item.getUsageStatementId());
			return new EvidenceLinkResponse(link.getId());
		} catch (DataIntegrityViolationException exception) {
			throw new ApiException(HttpStatus.CONFLICT, "이미 연결된 파일입니다.");
		}
	}

	@Transactional
	public EvidenceLinkResponse moveLink(Long currentUserId, Long projectId, Long linkId, MoveEvidenceFileLinkRequest request) {
		projectAccessService.requireWritable(currentUserId, projectId);
		requireEvidenceType(request.evidenceTypeCode());
		EvidenceFileLink link = linkRepository.findProjectLink(projectId, linkId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "파일 연결을 찾을 수 없습니다."));
		UsageStatementItem targetItem = evidenceQueryService.requireProjectItem(projectId, request.targetItemId());
		Long previousItemId = link.getUsageStatementItemId();

		if (!previousItemId.equals(targetItem.getId())
				&& linkRepository.existsByUsageStatementItemIdAndFileId(targetItem.getId(), link.getFileId())) {
			throw new ApiException(HttpStatus.CONFLICT, "대상 상세항목에 이미 연결된 파일입니다.");
		}

		link.moveTo(targetItem.getId(), request.evidenceTypeCode());
		recalculateRequirements(previousItemId);
		recalculateRequirements(targetItem.getId());
		revertToDraftIfSupplementRequired(targetItem.getUsageStatementId());
		return new EvidenceLinkResponse(link.getId());
	}

	@Transactional
	public void deleteLink(Long currentUserId, Long projectId, Long linkId) {
		projectAccessService.requireWritable(currentUserId, projectId);
		EvidenceFileLink link = linkRepository.findProjectLink(projectId, linkId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "파일 연결을 찾을 수 없습니다."));
		Long itemId = link.getUsageStatementItemId();
		UsageStatementItem item = evidenceQueryService.requireProjectItem(projectId, itemId);
		linkRepository.delete(link);
		linkRepository.flush();
		recalculateRequirements(itemId);
		revertToDraftIfSupplementRequired(item.getUsageStatementId());
	}

	@Transactional
	public void deleteLinksForFile(Long fileId) {
		List<EvidenceFileLink> links = linkRepository.findByFileId(fileId);
		Set<Long> itemIds = links.stream().map(EvidenceFileLink::getUsageStatementItemId).collect(Collectors.toSet());
		linkRepository.deleteByFileId(fileId);
		linkRepository.flush();
		if (!itemIds.isEmpty()) {
			recalculateRequirementsForItems(itemIds);
		}
	}

	private void recalculateRequirements(Long itemId) {
		List<EvidenceRequirement> requirements = requirementRepository.findByUsageStatementItemIdAndActiveTrue(itemId);
		if (requirements.isEmpty()) {
			return;
		}

		Set<String> linkedTypes = linkRepository.findByUsageStatementItemId(itemId)
				.stream()
				.map(EvidenceFileLink::getEvidenceTypeCode)
				.collect(Collectors.toSet());

		for (EvidenceRequirement requirement : requirements) {
			requirement.updateSatisfied(linkedTypes.contains(requirement.getEvidenceTypeCode()));
		}
	}

	private void recalculateRequirementsForItems(Set<Long> itemIds) {
		List<EvidenceRequirement> requirements = requirementRepository.findByUsageStatementItemIdInAndActiveTrue(itemIds);
		if (requirements.isEmpty()) {
			return;
		}

		Map<Long, Set<String>> linkedTypesByItemId = linkRepository.findByUsageStatementItemIdIn(itemIds)
				.stream()
				.collect(Collectors.groupingBy(
						EvidenceFileLink::getUsageStatementItemId,
						Collectors.mapping(EvidenceFileLink::getEvidenceTypeCode, Collectors.toSet())
				));

		for (EvidenceRequirement requirement : requirements) {
			Set<String> linkedTypes = linkedTypesByItemId.getOrDefault(requirement.getUsageStatementItemId(), Set.of());
			requirement.updateSatisfied(linkedTypes.contains(requirement.getEvidenceTypeCode()));
		}
	}

	private void revertToDraftIfSupplementRequired(Long usageStatementId) {
		usageStatementRepository.findById(usageStatementId)
				.ifPresent(UsageStatement::revertToDraft);
	}

	private void requireEvidenceType(String evidenceTypeCode) {
		if (!codeLookupService.evidenceTypeExists(evidenceTypeCode)) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "지원하지 않는 증빙 타입입니다.");
		}
	}
}
