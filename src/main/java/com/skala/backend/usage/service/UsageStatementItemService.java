package com.skala.backend.usage.service;

import com.skala.backend.agent.client.FastApiAgentClient;
import com.skala.backend.evidence.repository.EvidenceFileLinkRepository;
import com.skala.backend.evidence.repository.EvidenceRequirementRepository;
import com.skala.backend.evidence.service.EvidenceQueryService;
import com.skala.backend.global.error.ApiException;
import com.skala.backend.project.service.CodeLookupService;
import com.skala.backend.project.service.ProjectAccessService;
import com.skala.backend.usage.domain.UsageStatementItem;
import com.skala.backend.usage.dto.UsageStatementItemRequests.ChangeCategoryRequest;
import com.skala.backend.usage.dto.UsageStatementItemRequests.CreateItemRequest;
import com.skala.backend.usage.dto.UsageStatementItemRequests.UpdateItemRequest;
import com.skala.backend.usage.dto.UsageStatementResponses.CreateItemResponse;
import com.skala.backend.usage.dto.UsageStatementResponses.UsageStatementItemResponse;
import com.skala.backend.usage.repository.UsageStatementItemRepository;
import com.skala.backend.usage.repository.UsageStatementRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
public class UsageStatementItemService {

	private final FastApiAgentClient fastApiAgentClient;
	private final ProjectAccessService projectAccessService;
	private final UsageStatementRepository statementRepository;
	private final UsageStatementItemRepository itemRepository;
	private final EvidenceFileLinkRepository linkRepository;
	private final EvidenceRequirementRepository requirementRepository;
	private final EvidenceQueryService evidenceQueryService;
	private final CodeLookupService codeLookupService;

	public UsageStatementItemService(
			FastApiAgentClient fastApiAgentClient,
			ProjectAccessService projectAccessService,
			UsageStatementRepository statementRepository,
			UsageStatementItemRepository itemRepository,
			EvidenceFileLinkRepository linkRepository,
			EvidenceRequirementRepository requirementRepository,
			EvidenceQueryService evidenceQueryService,
			CodeLookupService codeLookupService
	) {
		this.fastApiAgentClient = fastApiAgentClient;
		this.projectAccessService = projectAccessService;
		this.statementRepository = statementRepository;
		this.itemRepository = itemRepository;
		this.linkRepository = linkRepository;
		this.requirementRepository = requirementRepository;
		this.evidenceQueryService = evidenceQueryService;
		this.codeLookupService = codeLookupService;
	}

	@Transactional
	public CreateItemResponse createItem(Long currentUserId, Long projectId, Long usageStatementId, CreateItemRequest request) {
		projectAccessService.requireWritable(currentUserId, projectId);

		if (!statementRepository.existsByIdAndProjectId(usageStatementId, projectId)) {
			throw new ApiException(HttpStatus.NOT_FOUND, "사용내역서를 찾을 수 없습니다.");
		}

		if (!codeLookupService.categoryNames().containsKey(request.categoryCode())) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "존재하지 않는 카테고리 코드입니다.");
		}

		FastApiAgentClient.ClassifyResult classi = fastApiAgentClient.classifyItem(
				projectId,
				usageStatementId,
				request.categoryCode(),
				request.itemName(),
				request.usedOn(),
				request.unit(),
				request.quantity(),
				request.unitPrice(),
				request.totalAmount()
		);

		String assignedCategory = (classi != null && classi.categoryCode() != null)
				? classi.categoryCode()
				: request.categoryCode();

		UsageStatementItem item = UsageStatementItem.create(
				usageStatementId,
				assignedCategory,
				request.usedOn(),
				request.itemName(),
				request.unit(),
				request.quantity(),
				request.unitPrice(),
				request.totalAmount(),
				request.remark(),
				request.pageNo()
		);
		itemRepository.save(item);

		boolean categoryChanged = !request.categoryCode().equals(assignedCategory);
		return new CreateItemResponse(item.getId(), request.categoryCode(), assignedCategory, categoryChanged);
	}

	@Transactional
	public UsageStatementItemResponse updateItem(Long currentUserId, Long projectId, Long itemId, UpdateItemRequest request) {
		projectAccessService.requireWritable(currentUserId, projectId);

		UsageStatementItem item = itemRepository.findProjectItem(projectId, itemId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "세부항목을 찾을 수 없습니다."));

		item.update(
				request.usedOn(),
				request.itemName(),
				request.unit(),
				request.quantity(),
				request.unitPrice(),
				request.totalAmount(),
				request.remark(),
				request.pageNo()
		);

		return toItemResponse(item, codeLookupService.categoryNames());
	}

	@Transactional
	public void deleteItem(Long currentUserId, Long projectId, Long itemId) {
		projectAccessService.requireWritable(currentUserId, projectId);

		UsageStatementItem item = itemRepository.findProjectItem(projectId, itemId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "세부항목을 찾을 수 없습니다."));

		requirementRepository.deleteByUsageStatementItemId(item.getId());
		linkRepository.deleteByUsageStatementItemId(item.getId());
		itemRepository.delete(item);
	}

	@Transactional
	public UsageStatementItemResponse changeCategory(Long currentUserId, Long projectId, Long itemId, ChangeCategoryRequest request) {
		projectAccessService.requireWritable(currentUserId, projectId);

		UsageStatementItem item = itemRepository.findProjectItem(projectId, itemId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "세부항목을 찾을 수 없습니다."));

		Map<String, String> categoryNames = codeLookupService.categoryNames();
		if (!categoryNames.containsKey(request.categoryCode())) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "존재하지 않는 카테고리 코드입니다.");
		}

		item.changeCategory(request.categoryCode());

		return toItemResponse(item, categoryNames);
	}

	private UsageStatementItemResponse toItemResponse(UsageStatementItem item, Map<String, String> categoryNames) {
		Long itemId = item.getId();
		return new UsageStatementItemResponse(
				itemId,
				item.getCategoryCode(),
				categoryNames.getOrDefault(item.getCategoryCode(), item.getCategoryCode()),
				item.getUsedOn(),
				item.getItemName(),
				item.getUnit(),
				item.getQuantity(),
				item.getUnitPrice(),
				item.getTotalAmount(),
				item.getRemark(),
				item.getPageNo(),
				evidenceQueryService.evidenceFilesByItemIds(List.of(itemId)).getOrDefault(itemId, List.of()),
				evidenceQueryService.requirementsByItemIds(List.of(itemId)).getOrDefault(itemId, List.of())
		);
	}
}
