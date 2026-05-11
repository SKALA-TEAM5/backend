package com.skala.backend.agent.repository;

import com.skala.backend.agent.dto.SafeleeAgentDtos.EvidenceRequirementItemContext;
import com.skala.backend.agent.dto.SafeleeAgentDtos.EvidenceRequirementRecord;
import com.skala.backend.agent.dto.SafeleeAgentDtos.EvidenceTypeDefinition;
import com.skala.backend.agent.dto.SafeleeAgentDtos.LinkedEvidenceFileContext;
import com.skala.backend.global.error.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public class SafeleeAgentRepository {

	private final JdbcTemplate jdbcTemplate;

	public SafeleeAgentRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	public EvidenceRequirementItemContext requireItemContext(Long projectId, Long statementId, Long itemId) {
		List<EvidenceRequirementItemContext> rows = jdbcTemplate.query(
				"""
				SELECT
					p.id AS project_id,
					p.project_name,
					us.id AS usage_statement_id,
					us.report_month,
					us.revision_no,
					i.id AS item_id,
					i.category_code,
					c.name AS category_name,
					i.used_on,
					i.item_name,
					i.unit,
					i.quantity,
					i.unit_price,
					i.total_amount,
					i.remark,
					i.page_no
				FROM service.usage_statement_items i
				JOIN service.usage_statements us ON us.id = i.usage_statement_id
				JOIN service.projects p ON p.id = us.project_id
				JOIN service.usage_categories c ON c.code = i.category_code
				WHERE p.id = ?
					AND us.id = ?
					AND i.id = ?
				""",
				(rs, rowNum) -> new EvidenceRequirementItemContext(
						rs.getLong("project_id"),
						rs.getString("project_name"),
						rs.getLong("usage_statement_id"),
						rs.getDate("report_month").toLocalDate(),
						rs.getInt("revision_no"),
						rs.getLong("item_id"),
						rs.getString("category_code"),
						rs.getString("category_name"),
						rs.getDate("used_on").toLocalDate(),
						rs.getString("item_name"),
						rs.getString("unit"),
						rs.getBigDecimal("quantity"),
						rs.getBigDecimal("unit_price"),
						rs.getBigDecimal("total_amount"),
						rs.getString("remark"),
						rs.getInt("page_no")
				),
				projectId,
				statementId,
				itemId
		);
		if (rows.isEmpty()) {
			throw new ApiException(HttpStatus.NOT_FOUND, "프로젝트, 사용내역서 또는 항목을 찾을 수 없습니다.");
		}
		return rows.get(0);
	}

	public List<LinkedEvidenceFileContext> linkedFiles(Long itemId) {
		return jdbcTemplate.query(
				"""
				SELECT
					f.id AS file_id,
					f.original_filename,
					f.mime_type,
					f.uploaded_evidence_type_code,
					l.evidence_type_code AS linked_evidence_type_code,
					f.storage_key,
					f.captured_at,
					f.uploaded_at
				FROM service.evidence_file_links l
				JOIN service.files f ON f.id = l.file_id
				WHERE l.usage_statement_item_id = ?
					AND f.deleted_at IS NULL
				ORDER BY l.id ASC
				""",
				(rs, rowNum) -> new LinkedEvidenceFileContext(
						rs.getLong("file_id"),
						rs.getString("original_filename"),
						rs.getString("mime_type"),
						rs.getString("uploaded_evidence_type_code"),
						rs.getString("linked_evidence_type_code"),
						rs.getString("storage_key"),
						rs.getTimestamp("captured_at") == null ? null : rs.getTimestamp("captured_at").toInstant(),
						rs.getTimestamp("uploaded_at") == null ? null : rs.getTimestamp("uploaded_at").toInstant()
				),
				itemId
		);
	}

	public List<EvidenceTypeDefinition> evidenceTypes() {
		return jdbcTemplate.query(
				"""
				SELECT code, name, description
				FROM service.evidence_types
				ORDER BY code ASC
				""",
				(rs, rowNum) -> new EvidenceTypeDefinition(
						rs.getString("code"),
						rs.getString("name"),
						rs.getString("description")
				)
		);
	}

	public List<EvidenceRequirementRecord> activeRequirements(Long itemId) {
		return jdbcTemplate.query(
				"""
				SELECT evidence_type_code, is_satisfied, is_active
				FROM service.evidence_requirements
				WHERE usage_statement_item_id = ?
					AND is_active = true
				ORDER BY evidence_type_code ASC
				""",
				(rs, rowNum) -> new EvidenceRequirementRecord(
						rs.getString("evidence_type_code"),
						rs.getBoolean("is_satisfied"),
						rs.getBoolean("is_active")
				),
				itemId
		);
	}

	public boolean evidenceTypesExist(List<String> evidenceTypeCodes) {
		if (evidenceTypeCodes.isEmpty()) {
			return true;
		}
		String placeholders = String.join(",", evidenceTypeCodes.stream().map(code -> "?").toList());
		Integer count = jdbcTemplate.queryForObject(
				"SELECT count(*) FROM service.evidence_types WHERE code IN (" + placeholders + ")",
				Integer.class,
				evidenceTypeCodes.toArray()
		);
		return count != null && count == evidenceTypeCodes.size();
	}

	@Transactional
	public List<EvidenceRequirementRecord> replaceActiveRequirements(Long itemId, List<String> evidenceTypeCodes) {
		jdbcTemplate.update(
				"""
				UPDATE service.evidence_requirements
				SET is_active = false,
					updated_at = now()
				WHERE usage_statement_item_id = ?
					AND is_active = true
				""",
				itemId
		);

		for (String evidenceTypeCode : evidenceTypeCodes) {
			jdbcTemplate.update(
					"""
					INSERT INTO service.evidence_requirements (
						usage_statement_item_id,
						evidence_type_code,
						is_satisfied,
						is_active
					)
					VALUES (
						?,
						?,
						EXISTS (
							SELECT 1
							FROM service.evidence_file_links
							WHERE usage_statement_item_id = ?
								AND evidence_type_code = ?
						),
						true
					)
					""",
					itemId,
					evidenceTypeCode,
					itemId,
					evidenceTypeCode
			);
		}

		return activeRequirements(itemId);
	}
}
