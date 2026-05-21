package com.skala.backend.action.repository;

import com.skala.backend.action.domain.ActionRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ActionRequestRepository extends JpaRepository<ActionRequest, Long> {

	List<ActionRequest> findByProjectIdOrderByCreatedAtDesc(Long projectId);

	Optional<ActionRequest> findByIdAndProjectId(Long id, Long projectId);
}
