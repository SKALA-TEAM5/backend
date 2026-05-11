package com.skala.backend.agent.repository;

import com.skala.backend.global.error.ApiException;
import com.skala.backend.usage.domain.UsageStatement;
import com.skala.backend.usage.domain.UsageStatementItem;
import com.skala.backend.usage.domain.UsageStatementSummary;
import com.skala.backend.usage.repository.UsageStatementItemRepository;
import com.skala.backend.usage.repository.UsageStatementRepository;
import com.skala.backend.usage.repository.UsageStatementSummaryRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Repository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Repository
public class LawAgentRepository {

	private final UsageStatementRepository usageStatementRepository;
	private final UsageStatementItemRepository usageStatementItemRepository;
	private final UsageStatementSummaryRepository usageStatementSummaryRepository;

	public LawAgentRepository(
			UsageStatementRepository usageStatementRepository,
			UsageStatementItemRepository usageStatementItemRepository,
			UsageStatementSummaryRepository usageStatementSummaryRepository
	) {
		this.usageStatementRepository = usageStatementRepository;
		this.usageStatementItemRepository = usageStatementItemRepository;
		this.usageStatementSummaryRepository = usageStatementSummaryRepository;
	}

	public UsageStatement requireUsageStatement(Long projectId, Long usageStatementId) {
		return usageStatementRepository.findByIdAndProjectId(usageStatementId, projectId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "사용내역서를 찾을 수 없습니다."));
	}

	public List<Map<String, Object>> classificationLineItems(Long usageStatementId) {
		return usageStatementItemRepository.findByUsageStatementIdOrderByPageNoAscUsedOnAscIdAsc(usageStatementId)
				.stream()
				.map(this::classificationLineItem)
				.toList();
	}

	public List<Map<String, Object>> validationCategories(Long usageStatementId) {
		List<UsageStatementItem> items = usageStatementItemRepository.findByUsageStatementIdOrderByPageNoAscUsedOnAscIdAsc(usageStatementId);
		return usageStatementSummaryRepository.findByUsageStatementIdOrderByCategoryCodeAsc(usageStatementId)
				.stream()
				.map(summary -> validationCategory(summary, items))
				.toList();
	}

	private Map<String, Object> classificationLineItem(UsageStatementItem item) {
		Map<String, Object> lineItem = new LinkedHashMap<>();
		lineItem.put("rowId", item.getId());
		lineItem.put("usageStatementId", item.getUsageStatementId().toString());
		lineItem.put("givenCategoryCode", item.getCategoryCode());
		lineItem.put("usedOn", item.getUsedOn().toString());
		lineItem.put("itemName", item.getItemName());
		lineItem.put("unit", item.getUnit());
		lineItem.put("quantity", item.getQuantity());
		lineItem.put("unitPrice", item.getUnitPrice());
		lineItem.put("amount", item.getTotalAmount());
		return lineItem;
	}

	private Map<String, Object> validationCategory(UsageStatementSummary summary, List<UsageStatementItem> items) {
		Map<String, Object> category = new LinkedHashMap<>();
		category.put("categoryCode", summary.getCategoryCode());
		category.put("summary", Map.of(
				"previousAmount", summary.getPreviousAmount(),
				"currentAmount", summary.getCurrentAmount(),
				"cumulativeAmount", summary.getCumulativeAmount()
		));
		category.put("items", items.stream()
				.filter(item -> item.getCategoryCode().equals(summary.getCategoryCode()))
				.map(this::validationItem)
				.toList());
		return category;
	}

	private Map<String, Object> validationItem(UsageStatementItem item) {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("rowId", item.getId());
		payload.put("usedOn", item.getUsedOn().toString());
		payload.put("itemName", item.getItemName());
		payload.put("unit", item.getUnit());
		payload.put("quantity", item.getQuantity());
		payload.put("unitPrice", item.getUnitPrice());
		payload.put("amount", item.getTotalAmount());
		payload.put("remark", item.getRemark());
		return payload;
	}
}
