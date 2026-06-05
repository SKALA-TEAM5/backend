package com.skala.backend.project.repository;

import com.skala.backend.project.domain.Project;
import com.skala.backend.project.domain.ProjectStatusCode;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;

public interface ProjectRepository extends JpaRepository<Project, Long>, ProjectRepositoryCustom {

	@Query("""
			SELECT p
			FROM Project p
			WHERE (:keywordPattern IS NULL
					OR LOWER(p.projectName) LIKE :keywordPattern
					OR LOWER(p.contractNo) LIKE :keywordPattern)
				AND (:projectNamePattern IS NULL OR LOWER(p.projectName) LIKE :projectNamePattern)
				AND (:contractNoPattern IS NULL OR LOWER(p.contractNo) LIKE :contractNoPattern)
				AND (:status IS NULL OR p.status = :status)
				AND (:periodFrom IS NULL OR p.constructionEndDate >= :periodFrom)
				AND (:periodTo IS NULL OR p.constructionStartDate <= :periodTo)
				AND (:assigneeUserId IS NULL OR EXISTS (
					SELECT 1 FROM ProjectUserAssignment a
					WHERE a.project = p AND a.user.id = :assigneeUserId
				))
				AND (:visibleUserId IS NULL OR EXISTS (
					SELECT 1 FROM ProjectUserAssignment a
					WHERE a.project = p AND a.user.id = :visibleUserId
				))
			ORDER BY
				CASE p.status
					WHEN com.skala.backend.project.domain.ProjectStatusCode.ACTIVE THEN 1
					WHEN com.skala.backend.project.domain.ProjectStatusCode.SUSPENDED THEN 2
					ELSE 3
				END,
				p.constructionStartDate DESC,
				p.id DESC
			""")
	Page<Project> search(
			@Param("keywordPattern") String keywordPattern,
			@Param("projectNamePattern") String projectNamePattern,
			@Param("contractNoPattern") String contractNoPattern,
			@Param("assigneeUserId") Long assigneeUserId,
			@Param("status") ProjectStatusCode status,
			@Param("periodFrom") LocalDate periodFrom,
			@Param("periodTo") LocalDate periodTo,
			@Param("visibleUserId") Long visibleUserId,
			Pageable pageable
	);

	@Query(value = """
			SELECT COALESCE((
				SELECT us.cumulative_progress_rate
				FROM usage_statements us
				WHERE us.project_id = :projectId
				ORDER BY us.report_month DESC, us.revision_no DESC
				LIMIT 1
			), 0)
			""", nativeQuery = true)
	BigDecimal findLatestProgressRate(@Param("projectId") Long projectId);
}
