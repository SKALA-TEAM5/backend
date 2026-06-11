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
}
