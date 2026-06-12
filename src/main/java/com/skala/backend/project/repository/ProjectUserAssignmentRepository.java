package com.skala.backend.project.repository;

import com.skala.backend.project.domain.ProjectUserAssignment;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProjectUserAssignmentRepository extends JpaRepository<ProjectUserAssignment, Long> {

	@EntityGraph(attributePaths = {"user", "assignedBy"})
	List<ProjectUserAssignment> findByProjectIdOrderByIdAsc(Long projectId);

	@EntityGraph(attributePaths = {"user"})
	List<ProjectUserAssignment> findByProjectIdIn(Collection<Long> projectIds);

	long countByProjectId(Long projectId);

	boolean existsByProjectIdAndUserId(Long projectId, Long userId);

	Optional<ProjectUserAssignment> findByProjectIdAndUserId(Long projectId, Long userId);

	void deleteByProjectId(Long projectId);

	@Query(value = "SELECT COUNT(*) FROM service.project_user_assignments pua JOIN service.users u ON u.id = pua.user_id WHERE pua.project_id = :projectId AND u.role_code = 'admin'", nativeQuery = true)
	long countAdminsByProjectId(@Param("projectId") Long projectId);

	/**
	 * 현재 사용자가 배정된 프로젝트들에 함께 배정된 admin/user 후보를 distinct로 조회한다. (admin·user용)
	 */
	@Query(value = """
			SELECT DISTINCT u.id AS userId, u.real_name AS realName, u.role_code AS roleCode
			FROM service.users u
			JOIN service.project_user_assignments pua ON pua.user_id = u.id
			WHERE u.role_code IN ('admin', 'user')
				AND pua.project_id IN (
					SELECT pua_self.project_id
					FROM service.project_user_assignments pua_self
					WHERE pua_self.user_id = :currentUserId
				)
				AND (CAST(:keywordPattern AS text) IS NULL OR LOWER(u.real_name) LIKE :keywordPattern)
			ORDER BY u.real_name
			""", nativeQuery = true)
	List<AssigneeCandidateRow> findAssigneePoolByUser(
			@Param("currentUserId") Long currentUserId,
			@Param("keywordPattern") String keywordPattern
	);

	/**
	 * 프로젝트에 배정된 모든 admin/user 후보를 distinct로 조회한다. (전체 프로젝트가 보이는 agent용)
	 */
	@Query(value = """
			SELECT DISTINCT u.id AS userId, u.real_name AS realName, u.role_code AS roleCode
			FROM service.users u
			JOIN service.project_user_assignments pua ON pua.user_id = u.id
			WHERE u.role_code IN ('admin', 'user')
				AND (CAST(:keywordPattern AS text) IS NULL OR LOWER(u.real_name) LIKE :keywordPattern)
			ORDER BY u.real_name
			""", nativeQuery = true)
	List<AssigneeCandidateRow> findAllAssigneePool(@Param("keywordPattern") String keywordPattern);
}
