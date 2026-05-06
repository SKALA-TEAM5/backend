package com.skala.backend.project.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class CodeLookupService {

	private final JdbcTemplate jdbcTemplate;

	public CodeLookupService(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	public Map<String, String> categoryNames() {
		return jdbcTemplate.query(
						"SELECT code, name FROM service.usage_categories ORDER BY code ASC",
						(rs, rowNum) -> new CodeName(rs.getString("code"), rs.getString("name"))
				)
				.stream()
				.collect(Collectors.toMap(CodeName::code, CodeName::name));
	}

	public List<String> categoryCodesInDisplayOrder() {
		return jdbcTemplate.queryForList(
				"SELECT code FROM service.usage_categories ORDER BY code ASC",
				String.class
		);
	}

	public Map<String, String> evidenceTypeNames() {
		return jdbcTemplate.query(
						"SELECT code, name FROM service.evidence_types",
						(rs, rowNum) -> new CodeName(rs.getString("code"), rs.getString("name"))
				)
				.stream()
				.collect(Collectors.toMap(CodeName::code, CodeName::name));
	}

	public boolean evidenceTypeExists(String evidenceTypeCode) {
		Integer count = jdbcTemplate.queryForObject(
				"SELECT count(*) FROM service.evidence_types WHERE code = ?",
				Integer.class,
				evidenceTypeCode
		);
		return count != null && count > 0;
	}

	public Map<Long, Long> linkedItemCounts(List<Long> fileIds) {
		if (fileIds.isEmpty()) {
			return Map.of();
		}
		String placeholders = fileIds.stream().map(id -> "?").collect(Collectors.joining(","));
		return jdbcTemplate.query(
						"""
						SELECT file_id, count(DISTINCT usage_statement_item_id) AS linked_count
						FROM service.evidence_file_links
						WHERE file_id IN (%s)
						GROUP BY file_id
						""".formatted(placeholders),
						(rs, rowNum) -> new LongCount(rs.getLong("file_id"), rs.getLong("linked_count")),
						fileIds.toArray()
				)
				.stream()
				.collect(Collectors.toMap(LongCount::id, LongCount::count));
	}

	public Map<Long, Long> linkedFileCountsByStatement(List<Long> usageStatementIds) {
		if (usageStatementIds.isEmpty()) {
			return Map.of();
		}
		String placeholders = usageStatementIds.stream().map(id -> "?").collect(Collectors.joining(","));
		return jdbcTemplate.query(
						"""
						SELECT i.usage_statement_id, count(DISTINCT l.file_id) AS linked_count
						FROM service.usage_statement_items i
						LEFT JOIN service.evidence_file_links l ON l.usage_statement_item_id = i.id
						WHERE i.usage_statement_id IN (%s)
						GROUP BY i.usage_statement_id
						""".formatted(placeholders),
						(rs, rowNum) -> new LongCount(rs.getLong("usage_statement_id"), rs.getLong("linked_count")),
						usageStatementIds.toArray()
				)
				.stream()
				.collect(Collectors.toMap(LongCount::id, LongCount::count));
	}

	public Map<Long, Long> unsatisfiedRequirementCountsByStatement(List<Long> usageStatementIds) {
		if (usageStatementIds.isEmpty()) {
			return Map.of();
		}
		String placeholders = usageStatementIds.stream().map(id -> "?").collect(Collectors.joining(","));
		return jdbcTemplate.query(
						"""
						SELECT i.usage_statement_id, count(r.id) AS unsatisfied_count
						FROM service.usage_statement_items i
						LEFT JOIN service.evidence_requirements r
							ON r.usage_statement_item_id = i.id
							AND r.is_active = true
							AND r.is_satisfied = false
						WHERE i.usage_statement_id IN (%s)
						GROUP BY i.usage_statement_id
						""".formatted(placeholders),
						(rs, rowNum) -> new LongCount(rs.getLong("usage_statement_id"), rs.getLong("unsatisfied_count")),
						usageStatementIds.toArray()
				)
				.stream()
				.collect(Collectors.toMap(LongCount::id, LongCount::count));
	}

	private record CodeName(String code, String name) {
	}

	private record LongCount(Long id, Long count) {
	}
}
