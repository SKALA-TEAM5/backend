package com.skala.backend.agent.repository;

import com.skala.backend.evidence.domain.EvidenceFileLink;
import com.skala.backend.evidence.repository.EvidenceFileLinkRepository;
import com.skala.backend.file.domain.ProjectFile;
import com.skala.backend.file.repository.ProjectFileRepository;
import com.skala.backend.global.error.ApiException;
import com.skala.backend.usage.domain.UsageStatement;
import com.skala.backend.usage.domain.UsageStatementItem;
import com.skala.backend.usage.repository.UsageStatementItemRepository;
import com.skala.backend.usage.repository.UsageStatementRepository;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Repository
public class OcrAgentRepository {

	private final UsageStatementRepository usageStatementRepository;
	private final UsageStatementItemRepository usageStatementItemRepository;
	private final ProjectFileRepository projectFileRepository;
	private final EvidenceFileLinkRepository evidenceFileLinkRepository;
	private final JdbcTemplate jdbcTemplate;

	public OcrAgentRepository(
			UsageStatementRepository usageStatementRepository,
			UsageStatementItemRepository usageStatementItemRepository,
			ProjectFileRepository projectFileRepository,
			EvidenceFileLinkRepository evidenceFileLinkRepository,
			JdbcTemplate jdbcTemplate
	) {
		this.usageStatementRepository = usageStatementRepository;
		this.usageStatementItemRepository = usageStatementItemRepository;
		this.projectFileRepository = projectFileRepository;
		this.evidenceFileLinkRepository = evidenceFileLinkRepository;
		this.jdbcTemplate = jdbcTemplate;
	}

	public ProjectFile requireFile(Long projectId, Long fileId) {
		return projectFileRepository.findByIdAndProjectIdAndDeletedAtIsNull(fileId, projectId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "파일을 찾을 수 없습니다."));
	}

	public UsageStatement requireUsageStatement(Long projectId, Long usageStatementId) {
		return usageStatementRepository.findByIdAndProjectId(usageStatementId, projectId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "사용내역서를 찾을 수 없습니다."));
	}

	public UsageStatementItem requireProjectItem(Long projectId, Long itemId) {
		return usageStatementItemRepository.findProjectItem(projectId, itemId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "사용내역서 상세항목을 찾을 수 없습니다."));
	}

	// 사용내역서 업로드는 OCR 결과를 먼저 DB에 적재한 뒤 classification에 태우는 흐름입니다.
	// JPA 엔티티 생성자를 늘리지 않기 위해, 초기 스켈레톤에서는 JdbcTemplate insert로 최소 구현합니다.
	// 반환하는 lineItems는 방금 생성된 usage_statement_items.id를 rowId로 사용해 classification 요청에 바로 씁니다.
	@SuppressWarnings("unchecked")
	@Transactional
	public Map<String, Object> saveParsedUsageStatement(Long projectId, Long fileId, Map<String, Object> parseData) {
		Map<String, Object> statement = (Map<String, Object>) parseData.get("usage_statement");
		if (statement == null) {
			throw new ApiException(HttpStatus.BAD_GATEWAY, "사용내역서 OCR 결과에 usage_statement가 없습니다.");
		}

		Long usageStatementId = jdbcTemplate.queryForObject(
				"""
				INSERT INTO service.usage_statements (
					project_id,
					source_file_id,
					report_month,
					revision_no,
					document_written_date,
					cumulative_progress_rate
				) VALUES (?, ?, ?, ?, ?, ?)
				RETURNING id
				""",
				Long.class,
				projectId,
				fileId,
				sqlDate(statement.get("report_month")),
				intValue(statement.get("revision_no"), 1),
				sqlDate(statement.get("document_written_date")),
				decimalValue(statement.get("cumulative_progress_rate"))
		);

		List<Map<String, Object>> summaries = (List<Map<String, Object>>) parseData.getOrDefault("summaries", List.of());
		for (Map<String, Object> summary : summaries) {
			jdbcTemplate.update(
					"""
					INSERT INTO service.usage_statement_summaries (
						usage_statement_id,
						category_code,
						previous_amount,
						current_amount,
						cumulative_amount
					) VALUES (?, ?, ?, ?, ?)
					""",
					usageStatementId,
					stringValue(summary.get("category_code")),
					decimalValue(summary.get("previous_amount")),
					decimalValue(summary.get("current_amount")),
					decimalValue(summary.get("cumulative_amount"))
			);
		}

		List<Map<String, Object>> items = (List<Map<String, Object>>) parseData.getOrDefault("items", List.of());
		List<Map<String, Object>> lineItems = new java.util.ArrayList<>();
		for (Map<String, Object> item : items) {
			Long itemId = jdbcTemplate.queryForObject(
					"""
					INSERT INTO service.usage_statement_items (
						usage_statement_id,
						category_code,
						used_on,
						item_name,
						unit,
						quantity,
						unit_price,
						total_amount,
						remark,
						page_no
					) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
					RETURNING id
					""",
					Long.class,
					usageStatementId,
					stringValue(item.get("category_code")),
					sqlDate(item.get("used_on")),
					stringValue(item.get("item_name")),
					nullableString(item.get("unit")),
					decimalValue(item.get("quantity")),
					decimalValue(item.get("unit_price")),
					decimalValue(item.get("total_amount")),
					nullableString(item.get("remark")),
					intValue(item.get("page_no"), 1)
			);
			lineItems.add(classificationLineItem(usageStatementId, itemId, item));
		}

		Map<String, Object> result = new LinkedHashMap<>();
		result.put("usageStatementId", usageStatementId);
		result.put("lineItems", lineItems);
		return result;
	}

	public Long linkEvidenceFile(UsageStatementItem item, ProjectFile file) {
		if (evidenceFileLinkRepository.existsByUsageStatementItemIdAndFileId(item.getId(), file.getId())) {
			return evidenceFileLinkRepository.findByUsageStatementItemId(item.getId())
					.stream()
					.filter(link -> link.getFileId().equals(file.getId()))
					.findFirst()
					.map(EvidenceFileLink::getId)
					.orElse(null);
		}
		EvidenceFileLink link = evidenceFileLinkRepository.save(EvidenceFileLink.create(
				item.getId(),
				file.getId(),
				file.getUploadedEvidenceTypeCode()
		));
		return link.getId();
	}

	// classification API는 law.yaml 기준의 camelCase 필드를 기대합니다.
	// DB/OCR의 snake_case 필드를 여기서 한 번만 변환해 서비스 로직을 단순하게 둡니다.
	private Map<String, Object> classificationLineItem(Long usageStatementId, Long itemId, Map<String, Object> item) {
		Map<String, Object> lineItem = new LinkedHashMap<>();
		lineItem.put("rowId", itemId);
		lineItem.put("usageStatementId", usageStatementId);
		lineItem.put("givenCategoryCode", stringValue(item.get("category_code")));
		lineItem.put("usedOn", stringValue(item.get("used_on")));
		lineItem.put("itemName", stringValue(item.get("item_name")));
		lineItem.put("unit", nullableString(item.get("unit")));
		lineItem.put("quantity", decimalValue(item.get("quantity")));
		lineItem.put("unitPrice", decimalValue(item.get("unit_price")));
		lineItem.put("amount", decimalValue(item.get("total_amount")));
		return lineItem;
	}

	private Date sqlDate(Object value) {
		if (value == null) {
			throw new ApiException(HttpStatus.BAD_GATEWAY, "사용내역서 OCR 결과에 필수 날짜가 없습니다.");
		}
		return Date.valueOf(LocalDate.parse(value.toString()));
	}

	private String stringValue(Object value) {
		if (value == null || value.toString().isBlank()) {
			throw new ApiException(HttpStatus.BAD_GATEWAY, "사용내역서 OCR 결과에 필수 문자열이 없습니다.");
		}
		return value.toString();
	}

	private String nullableString(Object value) {
		return value == null ? null : value.toString();
	}

	private int intValue(Object value, int defaultValue) {
		if (value == null) {
			return defaultValue;
		}
		if (value instanceof Number number) {
			return number.intValue();
		}
		return Integer.parseInt(value.toString());
	}

	private BigDecimal decimalValue(Object value) {
		if (value == null) {
			return BigDecimal.ZERO;
		}
		if (value instanceof BigDecimal decimal) {
			return decimal;
		}
		if (value instanceof Number number) {
			return BigDecimal.valueOf(number.doubleValue());
		}
		return new BigDecimal(value.toString());
	}
}
