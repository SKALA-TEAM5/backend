package com.skala.backend.evidence.service;

import com.skala.backend.evidence.dto.EvidenceResponses.ArchiveCategoryListResponse;
import com.skala.backend.evidence.dto.EvidenceResponses.ArchiveCategoryResponse;
import com.skala.backend.evidence.dto.EvidenceResponses.ArchiveItemListResponse;
import com.skala.backend.evidence.dto.EvidenceResponses.ArchiveItemResponse;
import com.skala.backend.global.error.ApiException;
import com.skala.backend.project.service.CodeLookupService;
import com.skala.backend.project.service.ProjectAccessService;
import com.skala.backend.usage.domain.UsageStatement;
import com.skala.backend.usage.repository.UsageStatementRepository;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Service
public class EvidenceArchiveService {

	private final ProjectAccessService projectAccessService;
	private final UsageStatementRepository statementRepository;
	private final CodeLookupService codeLookupService;
	private final JdbcTemplate jdbcTemplate;

	public EvidenceArchiveService(
			ProjectAccessService projectAccessService,
			UsageStatementRepository statementRepository,
			CodeLookupService codeLookupService,
			JdbcTemplate jdbcTemplate
	) {
		this.projectAccessService = projectAccessService;
		this.statementRepository = statementRepository;
		this.codeLookupService = codeLookupService;
		this.jdbcTemplate = jdbcTemplate;
	}

	@Transactional(readOnly = true)
	public ArchiveCategoryListResponse listCategories(Long currentUserId, Long projectId, Long usageStatementId, LocalDate reportMonth) {
		projectAccessService.requireReadable(currentUserId, projectId);
		UsageStatement statement = resolveStatement(projectId, usageStatementId, reportMonth);
		Map<String, String> categoryNames = codeLookupService.categoryNames();

		if (statement == null) {
			List<ArchiveCategoryResponse> emptyItems = codeLookupService.categoryCodesInDisplayOrder()
					.stream()
					.map(code -> new ArchiveCategoryResponse(code, categoryNames.getOrDefault(code, code), 0, 0, 0, 0))
					.toList();
			return new ArchiveCategoryListResponse(projectId, emptyItems);
		}

		List<ArchiveCategoryResponse> items = jdbcTemplate.query(
				"""
				SELECT
					c.code AS category_code,
					c.name AS category_name,
					count(DISTINCT i.id) AS item_count,
					count(DISTINCT l.file_id) AS linked_file_count,
					count(l.id) AS link_count,
					count(r.id) AS unsatisfied_requirement_count
				FROM service.usage_categories c
				LEFT JOIN service.usage_statement_items i
					ON i.category_code = c.code
					AND i.usage_statement_id = ?
				LEFT JOIN service.evidence_file_links l ON l.usage_statement_item_id = i.id
				LEFT JOIN service.evidence_requirements r
					ON r.usage_statement_item_id = i.id
					AND r.is_active = true
					AND r.is_satisfied = false
				GROUP BY c.code, c.name
				ORDER BY c.code ASC
				""",
				(rs, rowNum) -> new ArchiveCategoryResponse(
						rs.getString("category_code"),
						rs.getString("category_name"),
						rs.getLong("item_count"),
						rs.getLong("linked_file_count"),
						rs.getLong("link_count"),
						rs.getLong("unsatisfied_requirement_count")
				),
				statement.getId()
		);
		return new ArchiveCategoryListResponse(projectId, items);
	}

	@Transactional(readOnly = true)
	public ArchiveItemListResponse listCategoryItems(Long currentUserId, Long projectId, String categoryCode, Long usageStatementId, LocalDate reportMonth) {
		projectAccessService.requireReadable(currentUserId, projectId);
		UsageStatement statement = resolveStatement(projectId, usageStatementId, reportMonth);
		if (statement == null) {
			return new ArchiveItemListResponse(projectId, categoryCode, List.of());
		}

		List<ArchiveItemResponse> items = jdbcTemplate.query(
				"""
				SELECT
					i.id AS item_id,
					i.usage_statement_id,
					s.report_month,
					i.used_on,
					i.item_name,
					i.unit,
					i.quantity,
					i.unit_price,
					i.total_amount,
					i.remark,
					i.page_no,
					count(DISTINCT l.file_id) AS linked_file_count,
					count(r.id) AS unsatisfied_requirement_count
				FROM service.usage_statement_items i
				JOIN service.usage_statements s ON s.id = i.usage_statement_id
				LEFT JOIN service.evidence_file_links l ON l.usage_statement_item_id = i.id
				LEFT JOIN service.evidence_requirements r
					ON r.usage_statement_item_id = i.id
					AND r.is_active = true
					AND r.is_satisfied = false
				WHERE i.usage_statement_id = ?
					AND i.category_code = ?
				GROUP BY i.id, i.usage_statement_id, s.report_month, i.used_on, i.item_name, i.unit, i.quantity, i.unit_price, i.total_amount, i.remark, i.page_no
				ORDER BY i.page_no ASC, i.used_on ASC, i.id ASC
				""",
				(rs, rowNum) -> new ArchiveItemResponse(
						rs.getLong("item_id"),
						rs.getLong("usage_statement_id"),
						toLocalDate(rs.getDate("report_month")),
						toLocalDate(rs.getDate("used_on")),
						rs.getString("item_name"),
						rs.getString("unit"),
						rs.getBigDecimal("quantity"),
						rs.getBigDecimal("unit_price"),
						rs.getBigDecimal("total_amount"),
						rs.getString("remark"),
						rs.getInt("page_no"),
						rs.getLong("linked_file_count"),
						rs.getLong("unsatisfied_requirement_count")
				),
				statement.getId(),
				categoryCode
		);
		return new ArchiveItemListResponse(projectId, categoryCode, items);
	}

	private UsageStatement resolveStatement(Long projectId, Long usageStatementId, LocalDate reportMonth) {
		if (usageStatementId != null) {
			return statementRepository.findById(usageStatementId)
					.filter(statement -> statement.getProjectId().equals(projectId))
					.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "사용내역서를 찾을 수 없습니다."));
		}
		if (reportMonth != null) {
			LocalDate firstDay = reportMonth.withDayOfMonth(1);
			return statementRepository.findFirstByProjectIdAndReportMonthOrderByRevisionNoDesc(projectId, firstDay)
					.orElse(null);
		}
		return statementRepository.findFirstByProjectIdOrderByReportMonthDescRevisionNoDesc(projectId)
				.orElse(null);
	}

	private LocalDate toLocalDate(Date value) {
		return value == null ? null : value.toLocalDate();
	}
}
