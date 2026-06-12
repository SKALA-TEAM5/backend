package com.skala.backend.evidence.service;

import com.skala.backend.evidence.domain.EvidenceFileLink;
import com.skala.backend.evidence.domain.EvidenceRequirement;
import com.skala.backend.evidence.dto.EvidenceResponses.ItemEvidenceFilesResponse;
import com.skala.backend.evidence.repository.EvidenceFileLinkRepository;
import com.skala.backend.evidence.repository.EvidenceRequirementRepository;
import com.skala.backend.file.domain.ProjectFile;
import com.skala.backend.file.repository.ProjectFileRepository;
import com.skala.backend.file.service.VisionDetectionParser;
import com.skala.backend.global.error.ApiException;
import com.skala.backend.project.service.CodeLookupService;
import com.skala.backend.project.service.ProjectAccessService;
import com.skala.backend.usage.domain.UsageStatementItem;
import com.skala.backend.usage.dto.UsageStatementResponses.EvidenceFileResponse;
import com.skala.backend.usage.dto.UsageStatementResponses.RequirementResponse;
import com.skala.backend.usage.repository.UsageStatementItemRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class EvidenceQueryService {

	private final ProjectAccessService projectAccessService;
	private final UsageStatementItemRepository itemRepository;
	private final EvidenceFileLinkRepository linkRepository;
	private final EvidenceRequirementRepository requirementRepository;
	private final ProjectFileRepository fileRepository;
	private final CodeLookupService codeLookupService;
	private final VisionDetectionParser visionDetectionParser;

	public EvidenceQueryService(
			ProjectAccessService projectAccessService,
			UsageStatementItemRepository itemRepository,
			EvidenceFileLinkRepository linkRepository,
			EvidenceRequirementRepository requirementRepository,
			ProjectFileRepository fileRepository,
			CodeLookupService codeLookupService,
			VisionDetectionParser visionDetectionParser
	) {
		this.projectAccessService = projectAccessService;
		this.itemRepository = itemRepository;
		this.linkRepository = linkRepository;
		this.requirementRepository = requirementRepository;
		this.fileRepository = fileRepository;
		this.codeLookupService = codeLookupService;
		this.visionDetectionParser = visionDetectionParser;
	}

	@Transactional(readOnly = true)
	public ItemEvidenceFilesResponse listItemFiles(Long currentUserId, Long projectId, Long itemId) {
		projectAccessService.requireReadable(currentUserId, projectId);
		itemRepository.findProjectItem(projectId, itemId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "상세항목을 찾을 수 없습니다."));

		return new ItemEvidenceFilesResponse(
				projectId,
				itemId,
				evidenceFilesByItemIds(List.of(itemId)).getOrDefault(itemId, List.of()),
				requirementsByItemIds(List.of(itemId)).getOrDefault(itemId, List.of())
		);
	}

	@Transactional(readOnly = true)
	public Map<Long, List<EvidenceFileResponse>> evidenceFilesByItemIds(Collection<Long> itemIds) {
		if (itemIds.isEmpty()) {
			return Map.of();
		}

		List<EvidenceFileLink> links = linkRepository.findByUsageStatementItemIdIn(itemIds);
		if (links.isEmpty()) {
			return Map.of();
		}

		Map<Long, ProjectFile> filesById = fileRepository.findByIdIn(
						links.stream().map(EvidenceFileLink::getFileId).collect(Collectors.toSet())
				)
				.stream()
				.collect(Collectors.toMap(ProjectFile::getId, Function.identity()));
		Map<String, String> evidenceTypeNames = codeLookupService.evidenceTypeNames();

		Map<Long, List<EvidenceFileResponse>> result = new HashMap<>();
		for (EvidenceFileLink link : links) {
			ProjectFile file = filesById.get(link.getFileId());
			if (file == null) {
				continue;
			}
			result.computeIfAbsent(link.getUsageStatementItemId(), ignored -> new ArrayList<>())
					.add(new EvidenceFileResponse(
							link.getId(),
							file.getId(),
							link.getEvidenceTypeCode(),
							evidenceTypeNames.getOrDefault(link.getEvidenceTypeCode(), link.getEvidenceTypeCode()),
							file.getOriginalFilename(),
							file.getMimeType(),
							file.getSizeBytes(),
							file.getCapturedAt(),
							file.getUploadedAt(),
							visionDetectionParser.parse(file.getDetail(), file.getUploadedEvidenceTypeCode())
					));
		}

		result.values().forEach(files -> files.sort(Comparator.comparing(EvidenceFileResponse::uploadedAt).reversed()));
		return result;
	}

	@Transactional(readOnly = true)
	public Map<Long, List<RequirementResponse>> requirementsByItemIds(Collection<Long> itemIds) {
		if (itemIds.isEmpty()) {
			return Map.of();
		}

		Map<String, String> evidenceTypeNames = codeLookupService.evidenceTypeNames();
		return requirementRepository.findByUsageStatementItemIdInAndActiveTrue(itemIds)
				.stream()
				.collect(Collectors.groupingBy(
						EvidenceRequirement::getUsageStatementItemId,
						Collectors.mapping(
								requirement -> new RequirementResponse(
										requirement.getEvidenceTypeCode(),
										evidenceTypeNames.getOrDefault(requirement.getEvidenceTypeCode(), requirement.getEvidenceTypeCode()),
										requirement.isSatisfied()
								),
								Collectors.toList()
						)
				));
	}

	@Transactional(readOnly = true)
	public List<RequirementResponse> listRequirements(Long currentUserId, Long projectId, Long itemId) {
		projectAccessService.requireReadable(currentUserId, projectId);
		itemRepository.findProjectItem(projectId, itemId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "상세항목을 찾을 수 없습니다."));
		Map<String, String> evidenceTypeNames = codeLookupService.evidenceTypeNames();
		return requirementRepository.findByUsageStatementItemIdAndActiveTrue(itemId)
				.stream()
				.map(r -> new RequirementResponse(
						r.getEvidenceTypeCode(),
						evidenceTypeNames.getOrDefault(r.getEvidenceTypeCode(), r.getEvidenceTypeCode()),
						r.isSatisfied()
				))
				.toList();
	}

	@Transactional(readOnly = true)
	public UsageStatementItem requireProjectItem(Long projectId, Long itemId) {
		return itemRepository.findProjectItem(projectId, itemId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "상세항목을 찾을 수 없습니다."));
	}
}
