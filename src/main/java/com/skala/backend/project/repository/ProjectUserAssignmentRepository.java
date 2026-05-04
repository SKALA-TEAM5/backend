package com.skala.backend.project.repository;

import com.skala.backend.project.domain.ProjectUserAssignment;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProjectUserAssignmentRepository extends JpaRepository<ProjectUserAssignment, Long> {

	@EntityGraph(attributePaths = {"user", "assignedBy"})
	List<ProjectUserAssignment> findByProjectIdOrderByIdAsc(Long projectId);

	long countByProjectId(Long projectId);

	boolean existsByProjectIdAndUserId(Long projectId, Long userId);

	Optional<ProjectUserAssignment> findByProjectIdAndUserId(Long projectId, Long userId);

	void deleteByProjectId(Long projectId);
}
