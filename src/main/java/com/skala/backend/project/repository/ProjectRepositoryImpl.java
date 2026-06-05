package com.skala.backend.project.repository;

import com.skala.backend.project.domain.ProjectSort;
import com.skala.backend.project.domain.ProjectStatusCode;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Date;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

@Repository
public class ProjectRepositoryImpl implements ProjectRepositoryCustom {

	private final EntityManager entityManager;

	public ProjectRepositoryImpl(EntityManager entityManager) {
		this.entityManager = entityManager;
	}

	@Override
	public Page<ProjectCardRow> searchCards(
			String keywordPattern,
			String projectNamePattern,
			String contractNoPattern,
			Long assigneeUserId,
			ProjectStatusCode status,
			LocalDate periodFrom,
			LocalDate periodTo,
			Long visibleUserId,
			ProjectSort sort,
			int page,
			int size
	) {
		String whereClause = """
				WHERE (CAST(:keywordPattern AS text) IS NULL
						OR LOWER(p.project_name) LIKE :keywordPattern
						OR LOWER(COALESCE(p.contract_no, '')) LIKE :keywordPattern)
					AND (CAST(:projectNamePattern AS text) IS NULL OR LOWER(p.project_name) LIKE :projectNamePattern)
					AND (CAST(:contractNoPattern AS text) IS NULL OR LOWER(COALESCE(p.contract_no, '')) LIKE :contractNoPattern)
					AND (CAST(:status AS text) IS NULL OR p.project_status_code = :status)
					AND (CAST(:periodFrom AS date) IS NULL OR p.construction_end_date >= :periodFrom)
					AND (CAST(:periodTo AS date) IS NULL OR p.construction_start_date <= :periodTo)
					AND (CAST(:assigneeUserId AS bigint) IS NULL OR EXISTS (
						SELECT 1
						FROM service.project_user_assignments pua_filter
						WHERE pua_filter.project_id = p.id
							AND pua_filter.user_id = :assigneeUserId
					))
					AND (CAST(:visibleUserId AS bigint) IS NULL OR EXISTS (
						SELECT 1
						FROM service.project_user_assignments pua_visible
						WHERE pua_visible.project_id = p.id
							AND pua_visible.user_id = :visibleUserId
					))
				""";

		String selectSql = """
				WITH latest_statement AS (
					SELECT DISTINCT ON (us.project_id)
						us.project_id,
						us.cumulative_progress_rate,
						us.status_code
					FROM service.usage_statements us
					ORDER BY us.project_id, us.report_month DESC, us.revision_no DESC
				),
				unchecked_matched_files AS (
					SELECT
						us.project_id,
						count(DISTINCT l.file_id) AS unchecked_matched_file_count
					FROM service.evidence_file_links l
					JOIN service.usage_statement_items i ON i.id = l.usage_statement_item_id
					JOIN service.usage_statements us ON us.id = i.usage_statement_id
					JOIN service.files f ON f.id = l.file_id
					WHERE l.checked_at IS NULL
						AND f.deleted_at IS NULL
					GROUP BY us.project_id
				)
				SELECT
					p.id,
					p.project_name,
					COALESCE((
						SELECT string_agg(u.real_name, '|' ORDER BY pua.id)
						FROM service.project_user_assignments pua
						JOIN service.users u ON u.id = pua.user_id
						WHERE pua.project_id = p.id
					), '') AS assignee_names,
					(
						SELECT count(*)
						FROM service.project_user_assignments pua_count
						WHERE pua_count.project_id = p.id
					) AS assignee_count,
					p.contract_no,
					p.construction_start_date,
					p.construction_end_date,
					COALESCE(ls.cumulative_progress_rate, 0) AS latest_progress_rate,
					p.project_status_code,
					COALESCE(umf.unchecked_matched_file_count, 0) AS unchecked_matched_file_count,
					ls.status_code AS latest_usage_statement_status_code,
					CASE p.project_status_code
						WHEN 'active' THEN 1
						WHEN 'suspended' THEN 2
						ELSE 3
					END AS status_rank
				FROM service.projects p
				LEFT JOIN latest_statement ls ON ls.project_id = p.id
				LEFT JOIN unchecked_matched_files umf ON umf.project_id = p.id
				""" + whereClause + "\nORDER BY " + sort.orderByClause();

		String countSql = """
				SELECT count(*)
				FROM service.projects p
				""" + whereClause;

		Query selectQuery = entityManager.createNativeQuery(selectSql);
		Query countQuery = entityManager.createNativeQuery(countSql);
		bindParameters(selectQuery, keywordPattern, projectNamePattern, contractNoPattern, assigneeUserId, status, periodFrom, periodTo, visibleUserId);
		bindParameters(countQuery, keywordPattern, projectNamePattern, contractNoPattern, assigneeUserId, status, periodFrom, periodTo, visibleUserId);

		selectQuery.setFirstResult((page - 1) * size);
		selectQuery.setMaxResults(size);

		@SuppressWarnings("unchecked")
		List<Object[]> rows = selectQuery.getResultList();
		List<ProjectCardRow> content = rows.stream()
				.map(this::toRow)
				.toList();

		long totalCount = toLong(countQuery.getSingleResult());
		return new PageImpl<>(content, PageRequest.of(page - 1, size), totalCount);
	}

	private void bindParameters(
			Query query,
			String keywordPattern,
			String projectNamePattern,
			String contractNoPattern,
			Long assigneeUserId,
			ProjectStatusCode status,
			LocalDate periodFrom,
			LocalDate periodTo,
			Long visibleUserId
	) {
		query.setParameter("keywordPattern", keywordPattern);
		query.setParameter("projectNamePattern", projectNamePattern);
		query.setParameter("contractNoPattern", contractNoPattern);
		query.setParameter("assigneeUserId", assigneeUserId);
		query.setParameter("status", status == null ? null : status.getValue());
		query.setParameter("periodFrom", periodFrom);
		query.setParameter("periodTo", periodTo);
		query.setParameter("visibleUserId", visibleUserId);
	}

	private ProjectCardRow toRow(Object[] row) {
		return new ProjectCardRow(
				toLong(row[0]),
				(String) row[1],
				toAssigneeNames(row[2]),
				toLong(row[3]),
				(String) row[4],
				toLocalDate(row[5]),
				toLocalDate(row[6]),
				(BigDecimal) row[7],
				ProjectStatusCode.from((String) row[8]),
				toLong(row[9]),
				(String) row[10]
		);
	}

	private List<String> toAssigneeNames(Object value) {
		if (!(value instanceof String names) || names.isBlank()) {
			return List.of();
		}
		return Arrays.stream(names.split("\\|"))
				.filter(name -> !name.isBlank())
				.toList();
	}

	private LocalDate toLocalDate(Object value) {
		if (value instanceof LocalDate localDate) {
			return localDate;
		}
		return ((Date) value).toLocalDate();
	}

	private long toLong(Object value) {
		if (value instanceof BigInteger bigInteger) {
			return bigInteger.longValue();
		}
		if (value instanceof Number number) {
			return number.longValue();
		}
		throw new IllegalArgumentException("숫자 타입으로 변환할 수 없습니다.");
	}
}
